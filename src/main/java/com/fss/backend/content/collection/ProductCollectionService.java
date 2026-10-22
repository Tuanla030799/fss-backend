package com.fss.backend.content.collection;

import com.fss.backend.catalog.product.ProductRepository;
import com.fss.backend.catalog.product.ProductSummary;
import com.fss.backend.common.ErrorCode;
import com.fss.backend.common.PageResult;
import com.fss.backend.content.html.HtmlContentImageUsageService;
import com.fss.backend.content.html.HtmlSanitizerService;
import com.fss.backend.file.FileReferenceService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductCollectionService {
    private final ProductCollectionRepository repository;
    private final ProductRepository productRepository;
    private final EcommerceSupport support;
    private final HtmlSanitizerService htmlSanitizerService;
    private final HtmlContentImageUsageService htmlContentImageUsageService;
    private final FileReferenceService fileReferenceService;

    public ProductCollectionService(ProductCollectionRepository repository, ProductRepository productRepository, EcommerceSupport support,
                                    HtmlSanitizerService htmlSanitizerService,
                                    HtmlContentImageUsageService htmlContentImageUsageService,
                                    FileReferenceService fileReferenceService) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.support = support;
        this.htmlSanitizerService = htmlSanitizerService;
        this.htmlContentImageUsageService = htmlContentImageUsageService;
        this.fileReferenceService = fileReferenceService;
    }

    public PageResult<ProductCollection> listPublicCollections(String keyword, int page, int limit) {
        int safeLimit = support.safeLimit(limit);
        List<ProductCollection> items = repository.listCollections(true, null, keyword, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withCollectionUrl).toList();
        return support.pageResult(items, page, safeLimit, repository.countCollections(true, null, keyword));
    }

    public ProductCollectionDetail getPublicCollection(String slug, int page, int limit) {
        return collectionDetail(repository.findCollectionBySlug(slug, true), true, page, limit);
    }

    public PageResult<ProductCollection> listAdminCollections(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<ProductCollection> items = repository.listCollections(false, normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withCollectionUrl).toList();
        return support.pageResult(items, page, safeLimit, repository.countCollections(false, normalizedStatus, keyword));
    }

    public ProductCollectionDetail getAdminCollection(UUID id) {
        return collectionDetail(repository.findCollectionById(id), false, 1, 100);
    }

    @Transactional
    public UUID createCollection(ProductCollectionRequest request) {
        UUID id = UUID.randomUUID();
        support.activateFile(request.fileId());
        String excerpt = collectionExcerpt(request);
        String descriptionHtml = sanitizeCollectionDescription(request);
        repository.insertCollection(id, request.name().trim(), uniqueSlug(request.slug(), request.name(), null),
                excerpt, descriptionHtml, htmlContentImageUsageService.filePathList(descriptionHtml), request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), members(request.products()), support.adminId());
        htmlContentImageUsageService.activateImages(descriptionHtml);
        return id;
    }

    @Transactional
    public void updateCollection(UUID id, ProductCollectionRequest request) {
        ProductCollection existing = repository.findCollectionById(id);
        support.requireFound(existing != null, ErrorCode.COLLECTION_NOT_FOUND, "Collection not found");
        support.activateFile(request.fileId());
        String excerpt = collectionExcerpt(request);
        String descriptionHtml = sanitizeCollectionDescription(request);
        repository.updateCollection(id, request.name().trim(), uniqueSlug(request.slug(), request.name(), id),
                excerpt, descriptionHtml, htmlContentImageUsageService.filePathList(descriptionHtml), request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), members(request.products()), support.adminId());
        htmlContentImageUsageService.activateImages(descriptionHtml);
        fileReferenceService.releaseFile(existing.fileId());
        fileReferenceService.releaseRemovedHtmlImages(existing.descriptionHtml(), descriptionHtml);
    }

    @Transactional
    public void deleteCollection(UUID id) {
        ProductCollection existing = repository.findCollectionById(id);
        support.requireFound(existing != null, ErrorCode.COLLECTION_NOT_FOUND, "Collection not found");
        repository.softDeleteCollection(id, support.adminId());
        fileReferenceService.releaseFile(existing.fileId());
        fileReferenceService.releaseRemovedHtmlImages(existing.descriptionHtml(), null);
    }

    private ProductCollectionDetail collectionDetail(ProductCollection collection, boolean publicOnly, int page, int limit) {
        support.requireFound(collection != null, ErrorCode.COLLECTION_NOT_FOUND, "Collection not found");
        ProductCollection normalized = withCollectionUrl(collection);
        int safeLimit = support.safeLimit(limit);
        List<ProductSummary> products = repository.listCollectionProducts(collection.id(), publicOnly, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withProductUrl).toList();
        PageResult<ProductSummary> pagedProducts = support.pageResult(products, page, safeLimit,
                repository.countCollectionProducts(collection.id(), publicOnly));
        return new ProductCollectionDetail(normalized.id(), normalized.name(), normalized.slug(),
                normalized.excerpt(), normalized.descriptionHtml(),
                normalized.fileId(), normalized.imageUrl(), normalized.status(),
                normalized.sortOrder(), normalized.productCount(), normalized.createdAt(), normalized.updatedAt(), pagedProducts);
    }

    private String collectionExcerpt(ProductCollectionRequest request) {
        return support.trimToNull(request.excerpt());
    }

    private String sanitizeCollectionDescription(ProductCollectionRequest request) {
        return htmlSanitizerService.sanitize(request.descriptionHtml());
    }

    /** Old behavior: rows were (collection, product) unique and a repeated product kept the last sortOrder. */
    private List<CollectionDocument.Member> members(List<CollectionProductRequest> products) {
        if (products == null) return List.of();
        java.util.LinkedHashMap<UUID, CollectionDocument.Member> members = new java.util.LinkedHashMap<>();
        java.time.Instant now = java.time.Instant.now();
        for (CollectionProductRequest product : products) {
            support.require(productRepository.existsLive(product.productId()), ErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + product.productId());
            CollectionDocument.Member previous = members.get(product.productId());
            members.put(product.productId(), new CollectionDocument.Member(product.productId(), support.nz(product.sortOrder()),
                    previous == null ? now : previous.createdAt()));
        }
        return List.copyOf(members.values());
    }

    private String uniqueSlug(String slug, String name, UUID exclude) {
        String normalized = support.uniqueSlug(slug, name);
        support.requireUnique(!repository.slugExists(normalized, exclude), ErrorCode.SLUG_ALREADY_EXISTS, "Collection slug already exists");
        return normalized;
    }

    private ProductCollection withCollectionUrl(ProductCollection collection) {
        return collection == null ? null : new ProductCollection(collection.id(),
                collection.name(), collection.slug(), collection.excerpt(),
                htmlContentImageUsageService.rewriteForOutput(collection.descriptionHtml()),
                collection.fileId(), support.publicUrl(collection.imageUrl()), collection.status(), collection.sortOrder(),
                collection.productCount(), collection.createdAt(), collection.updatedAt());
    }

    private ProductSummary withProductUrl(ProductSummary product) {
        return product == null ? null : new ProductSummary(product.id(), product.categoryId(),
                product.categoryName(), product.brandId(), product.brandName(), product.brandSlug(), support.publicUrl(product.brandSizeGuideUrl()), product.gender(), product.name(), product.slug(), product.shortDescription(), product.status(),
                product.isFeatured(), product.featuredOrder(), product.minPrice(), product.minSalePrice(), product.totalStock(),
                product.primaryFileId(), support.publicUrl(product.primaryImageUrl()), product.createdAt(), product.updatedAt());
    }
}
