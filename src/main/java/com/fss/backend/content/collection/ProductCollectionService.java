package com.fss.backend.content.collection;

import com.fss.backend.catalog.product.ProductMapper;
import com.fss.backend.catalog.product.ProductSummary;
import com.fss.backend.content.html.HtmlContentImageUsageService;
import com.fss.backend.content.html.HtmlSanitizerService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductCollectionService {
    private final ProductCollectionMapper mapper;
    private final ProductMapper productMapper;
    private final EcommerceSupport support;
    private final HtmlSanitizerService htmlSanitizerService;
    private final HtmlContentImageUsageService htmlContentImageUsageService;

    public ProductCollectionService(ProductCollectionMapper mapper, ProductMapper productMapper, EcommerceSupport support,
                                    HtmlSanitizerService htmlSanitizerService,
                                    HtmlContentImageUsageService htmlContentImageUsageService) {
        this.mapper = mapper;
        this.productMapper = productMapper;
        this.support = support;
        this.htmlSanitizerService = htmlSanitizerService;
        this.htmlContentImageUsageService = htmlContentImageUsageService;
    }

    public List<ProductCollection> listPublicCollections(String keyword, int page, int limit) {
        return mapper.listCollections(true, null, keyword, support.safeLimit(limit), support.offset(page, limit))
                .stream().map(this::withCollectionUrl).toList();
    }

    public ProductCollectionDetail getPublicCollection(String slug, int page, int limit) {
        return collectionDetail(mapper.findCollectionBySlug(slug, true), true, page, limit);
    }

    public List<ProductCollection> listAdminCollections(String status, String keyword, int page, int limit) {
        return mapper.listCollections(false, support.normalizeStatusNullable(status), keyword, support.safeLimit(limit), support.offset(page, limit))
                .stream().map(this::withCollectionUrl).toList();
    }

    public ProductCollectionDetail getAdminCollection(UUID id) {
        return collectionDetail(mapper.findCollectionById(id), false, 1, 100);
    }

    @Transactional
    public UUID createCollection(ProductCollectionRequest request) {
        UUID id = UUID.randomUUID();
        support.activateFile(request.fileId());
        String excerpt = collectionExcerpt(request);
        String descriptionHtml = sanitizeCollectionDescription(request);
        mapper.insertCollection(id, request.name().trim(), uniqueSlug(request.slug(), request.name(), null),
                excerpt, descriptionHtml, request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
        htmlContentImageUsageService.activateImages(descriptionHtml);
        replaceProducts(id, request.products());
        return id;
    }

    @Transactional
    public void updateCollection(UUID id, ProductCollectionRequest request) {
        support.require(mapper.findCollectionById(id) != null, "Collection not found");
        support.activateFile(request.fileId());
        String excerpt = collectionExcerpt(request);
        String descriptionHtml = sanitizeCollectionDescription(request);
        mapper.updateCollection(id, request.name().trim(), uniqueSlug(request.slug(), request.name(), id),
                excerpt, descriptionHtml, request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
        htmlContentImageUsageService.activateImages(descriptionHtml);
        replaceProducts(id, request.products());
    }

    @Transactional
    public void deleteCollection(UUID id) {
        support.require(mapper.findCollectionById(id) != null, "Collection not found");
        mapper.softDeleteCollection(id, support.adminId());
    }

    private ProductCollectionDetail collectionDetail(ProductCollection collection, boolean publicOnly, int page, int limit) {
        support.require(collection != null, "Collection not found");
        ProductCollection normalized = withCollectionUrl(collection);
        List<ProductSummary> products = mapper.listCollectionProducts(collection.id(), publicOnly, support.safeLimit(limit), support.offset(page, limit))
                .stream().map(this::withProductUrl).toList();
        return new ProductCollectionDetail(normalized.id(), normalized.name(), normalized.slug(),
                normalized.excerpt(), normalized.descriptionHtml(),
                normalized.fileId(), normalized.imageUrl(), normalized.status(),
                normalized.sortOrder(), normalized.productCount(), normalized.createdAt(), products);
    }

    private String collectionExcerpt(ProductCollectionRequest request) {
        return support.trimToNull(request.excerpt());
    }

    private String sanitizeCollectionDescription(ProductCollectionRequest request) {
        return htmlSanitizerService.sanitize(request.descriptionHtml());
    }

    private void replaceProducts(UUID collectionId, List<CollectionProductRequest> products) {
        mapper.deleteCollectionProducts(collectionId);
        if (products == null) return;
        for (CollectionProductRequest product : products) {
            support.require(productMapper.findProductSummaryById(product.productId()) != null, "Product not found: " + product.productId());
            mapper.insertCollectionProduct(collectionId, product.productId(), support.nz(product.sortOrder()));
        }
    }

    private String uniqueSlug(String slug, String name, UUID exclude) {
        String normalized = support.uniqueSlug(slug, name);
        support.require(mapper.countCollectionSlug(normalized, exclude) == 0, "Collection slug already exists");
        return normalized;
    }

    private ProductCollection withCollectionUrl(ProductCollection collection) {
        return collection == null || collection.imageUrl() == null ? collection : new ProductCollection(collection.id(),
                collection.name(), collection.slug(), collection.excerpt(), collection.descriptionHtml(),
                collection.fileId(), support.publicUrl(collection.imageUrl()), collection.status(), collection.sortOrder(),
                collection.productCount(), collection.createdAt());
    }

    private ProductSummary withProductUrl(ProductSummary product) {
        return product == null || product.primaryImageUrl() == null ? product : new ProductSummary(product.id(), product.categoryId(),
                product.categoryName(), product.brandId(), product.brandName(), product.brandSlug(), product.gender(), product.name(), product.slug(), product.shortDescription(), product.status(),
                product.isFeatured(), product.featuredOrder(), product.minPrice(), product.minSalePrice(), product.totalStock(),
                product.primaryFileId(), support.publicUrl(product.primaryImageUrl()), product.createdAt());
    }
}
