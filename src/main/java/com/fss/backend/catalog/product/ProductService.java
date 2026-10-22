package com.fss.backend.catalog.product;

import com.fss.backend.common.ApiException;
import com.fss.backend.catalog.brand.BrandRepository;
import com.fss.backend.catalog.category.CategoryRepository;
import com.fss.backend.catalog.sku.Sku;
import com.fss.backend.catalog.sku.SkuRequest;
import com.fss.backend.common.ErrorCode;
import com.fss.backend.common.PageResult;
import com.fss.backend.content.html.HtmlContentImageUsageService;
import com.fss.backend.content.html.HtmlSanitizerService;
import com.fss.backend.file.FileReferenceService;
import com.fss.backend.masterdata.ColorOption;
import com.fss.backend.masterdata.SizeColorRepository;
import com.fss.backend.masterdata.SizeOption;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProductService {
    private static final String DEFAULT_SIZE_VALUE = "DEFAULT";

    private final ProductRepository repository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final SizeColorRepository sizeColorRepository;
    private final EcommerceSupport support;
    private final HtmlSanitizerService htmlSanitizerService;
    private final HtmlContentImageUsageService htmlContentImageUsageService;
    private final FileReferenceService fileReferenceService;

    public ProductService(ProductRepository repository, CategoryRepository categoryRepository, BrandRepository brandRepository,
                          SizeColorRepository sizeColorRepository, EcommerceSupport support,
                          HtmlSanitizerService htmlSanitizerService,
                          HtmlContentImageUsageService htmlContentImageUsageService,
                          FileReferenceService fileReferenceService) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.sizeColorRepository = sizeColorRepository;
        this.support = support;
        this.htmlSanitizerService = htmlSanitizerService;
        this.htmlContentImageUsageService = htmlContentImageUsageService;
        this.fileReferenceService = fileReferenceService;
    }

    public PageResult<ProductSummary> listPublicProducts(List<UUID> categoryIds, String categorySlug, List<UUID> brandIds, String brandSlug, List<String> genders, String keyword, List<String> sizes,
                                                         List<String> colors, BigDecimal minPrice, BigDecimal maxPrice,
                                                         String sort, int page, int limit) {
        ProductSort productSort = ProductSort.parse(sort);
        List<UUID> normalizedCategoryIds = normalizeUuidFilters(categoryIds);
        List<UUID> normalizedBrandIds = normalizeUuidFilters(brandIds);
        List<String> normalizedGenders = normalizeGenderFilters(genders);
        String normalizedKeyword = support.trimToNull(keyword);
        List<String> normalizedSizes = normalizeTextFilters(sizes);
        List<String> normalizedColors = normalizeTextFilters(colors);
        int safeLimit = support.safeLimit(limit);
        ProductFilter filter = new ProductFilter(true, null, normalizedCategoryIds, categorySlug, normalizedBrandIds, brandSlug,
                normalizedGenders, normalizedKeyword, normalizedSizes, normalizedColors, minPrice, maxPrice, false);
        List<ProductSummary> items = repository.listProducts(filter, productSort, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withProductUrl).toList();
        long total = repository.countProducts(filter);
        return support.pageResult(items, page, safeLimit, total);
    }

    public PageResult<ProductSummary> listAdminProducts(String status, UUID categoryId, UUID brandId, String gender, String size, String color, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeProductStatusNullable(status);
        List<UUID> normalizedCategoryIds = normalizeUuidFilters(categoryId == null ? null : List.of(categoryId));
        List<UUID> normalizedBrandIds = normalizeUuidFilters(brandId == null ? null : List.of(brandId));
        List<String> normalizedGenders = normalizeGenderFilters(gender == null ? null : List.of(gender));
        String normalizedKeyword = support.trimToNull(keyword);
        List<String> normalizedSizes = normalizeTextFilters(size == null ? null : List.of(size));
        List<String> normalizedColors = normalizeTextFilters(color == null ? null : List.of(color));
        int safeLimit = support.safeLimit(limit);
        ProductFilter filter = new ProductFilter(false, normalizedStatus, normalizedCategoryIds, null, normalizedBrandIds, null,
                normalizedGenders, normalizedKeyword, normalizedSizes, normalizedColors, null, null, false);
        List<ProductSummary> items = repository.listProducts(filter, ProductSort.NEWEST, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withProductUrl).toList();
        long total = repository.countProducts(filter);
        return support.pageResult(items, page, safeLimit, total);
    }

    @Cacheable(value = "featuredProducts", key = "#limit")
    public List<ProductSummary> featuredProducts(int limit) {
        return repository.listProducts(ProductFilter.featured(), ProductSort.FEATURED, Math.min(Math.max(limit, 1), 30), 0)
                .stream().map(this::withProductUrl).toList();
    }

    public ProductDetail getPublicProduct(String slug) {
        return productDetail(repository.findProductSummaryBySlug(slug, true), true);
    }

    public ProductDetail getAdminProduct(UUID id) {
        return productDetail(repository.findProductSummaryById(id), false);
    }

    @Transactional
    @CacheEvict(value = "featuredProducts", allEntries = true)
    public UUID createProduct(ProductRequest request) {
        requireCategoryExists(request.categoryId());
        requireBrandExists(request.brandId());
        UUID id = UUID.randomUUID();
        ContentPayload content = contentPayload(request);
        repository.insertProduct(id, request.categoryId(), request.brandId(), support.normalizeProductGenderDefault(request.gender()),
                support.trimRequired(request.name(), "Product name"), uniqueSlug(request.slug(), request.name(), null),
                support.trimToNull(request.shortDescription()), content.html(),
                htmlContentImageUsageService.filePathList(content.html()),
                support.normalizeProductStatusDefault(request.status()),
                support.bool(request.isFeatured()), support.nz(request.featuredOrder()), support.adminId());
        htmlContentImageUsageService.activateImages(content.html());
        replaceProductChildren(id, request.images(), request.variants(), request.skus());
        return id;
    }

    @Transactional
    @CacheEvict(value = "featuredProducts", allEntries = true)
    public void updateProduct(UUID id, ProductRequest request) {
        requireProductExists(id);
        requireCategoryExists(request.categoryId());
        requireBrandExists(request.brandId());
        String previousDescriptionHtml = repository.findProductDescriptionHtml(id);
        List<ProductImage> previousImages = repository.listImages(id);
        List<ProductVariant> previousVariants = repository.listVariants(id, false);
        ContentPayload content = contentPayload(request);
        repository.updateProduct(id, request.categoryId(), request.brandId(), support.normalizeProductGenderDefault(request.gender()),
                support.trimRequired(request.name(), "Product name"), uniqueSlug(request.slug(), request.name(), id),
                support.trimToNull(request.shortDescription()), content.html(),
                htmlContentImageUsageService.filePathList(content.html()),
                support.normalizeProductStatusDefault(request.status()),
                support.bool(request.isFeatured()), support.nz(request.featuredOrder()), support.adminId());
        htmlContentImageUsageService.activateImages(content.html());
        replaceProductChildren(id, request.images(), request.variants(), request.skus());
        fileReferenceService.releaseFiles(fileIds(previousImages));
        fileReferenceService.releaseFiles(variantImageFileIds(previousVariants));
        fileReferenceService.releaseRemovedHtmlImages(previousDescriptionHtml, content.html());
    }

    @Transactional
    @CacheEvict(value = "featuredProducts", allEntries = true)
    public void updateProductStatus(UUID id, String status) {
        requireProductExists(id);
        repository.updateProductStatus(id, support.normalizeProductStatusRequired(status), support.adminId());
    }

    @Transactional
    @CacheEvict(value = "featuredProducts", allEntries = true)
    public void deleteProduct(UUID id) {
        requireProductExists(id);
        String previousDescriptionHtml = repository.findProductDescriptionHtml(id);
        List<ProductImage> previousImages = repository.listImages(id);
        List<ProductVariant> previousVariants = repository.listVariants(id, false);
        repository.softDeleteProduct(id, support.adminId());
        fileReferenceService.releaseFiles(fileIds(previousImages));
        fileReferenceService.releaseFiles(variantImageFileIds(previousVariants));
        fileReferenceService.releaseRemovedHtmlImages(previousDescriptionHtml, null);
    }

    /** In public, variants and SKUs that cannot be bought (not ACTIVE, or SKU of a hidden variant) are left out. */
    private ProductDetail productDetail(ProductSummary summary, boolean publicOnly) {
        support.requireFound(summary != null, ErrorCode.PRODUCT_NOT_FOUND, "Product not found");
        List<ProductImage> images = repository.listImages(summary.id()).stream().map(this::withImageUrl).toList();
        List<ProductVariant> variants = repository.listVariants(summary.id(), publicOnly).stream().map(this::withVariantUrl).toList();
        List<Sku> skus = repository.listSkus(summary.id(), publicOnly);
        return new ProductDetail(summary.id(), summary.categoryId(), summary.categoryName(), summary.brandId(), summary.brandName(),
                summary.brandSlug(), support.publicUrl(summary.brandSizeGuideUrl()), summary.gender(), summary.name(), summary.slug(),
                summary.shortDescription(), "{}", loadDescriptionHtml(summary.id()), summary.status(), summary.isFeatured(),
                summary.featuredOrder(), summary.createdAt(), summary.updatedAt(), images, variants, skus);
    }

    private String loadDescriptionHtml(UUID productId) {
        return htmlContentImageUsageService.rewriteForOutput(repository.findProductDescriptionHtml(productId));
    }

    private ContentPayload contentPayload(ProductRequest request) {
        String html = request.descriptionHtml();
        if (request.descriptionJson() != null && request.descriptionJson().isTextual()) {
            String text = request.descriptionJson().asText();
            if (html == null && looksLikeHtml(text)) {
                html = text;
            }
        }
        return new ContentPayload(htmlSanitizerService.sanitize(html));
    }

    private boolean looksLikeHtml(String value) {
        return value != null && value.trim().startsWith("<");
    }

    private void replaceProductChildren(UUID productId, List<ProductImageRequest> images, List<VariantRequest> variants, List<SkuRequest> skus) {
        List<ProductDocument.Image> imageDocs = new java.util.ArrayList<>();
        if (images != null) {
            java.time.Instant now = java.time.Instant.now();
            for (ProductImageRequest image : images) {
                support.activateFile(image.fileId());
                imageDocs.add(new ProductDocument.Image(UUID.randomUUID(), image.fileId(), image.altText(),
                        support.def(image.imageType(), "GALLERY"), support.nz(image.sortOrder()), support.bool(image.isPrimary()),
                        now.plusMillis(imageDocs.size())));
            }
        }
        repository.replaceImages(productId, imageDocs);

        Map<UUID, UUID> clientVariantIds = new HashMap<>();
        java.util.Set<UUID> keepVariantIds = new java.util.HashSet<>();
        if (variants != null) {
            for (VariantRequest variant : variants) {
                UUID id = saveVariant(productId, variant);
                keepVariantIds.add(id);
                if (variant.clientId() != null) clientVariantIds.put(variant.clientId(), id);
            }
        }

        java.util.Set<UUID> keepSkuIds = new java.util.HashSet<>();
        if (skus != null) {
            for (SkuRequest sku : skus) {
                UUID id = saveSku(productId, sku, clientVariantIds);
                keepSkuIds.add(id);
            }
        }
        repository.softDeleteMissingSkus(productId, keepSkuIds);
        repository.softDeleteMissingVariants(productId, keepVariantIds);
    }

    /** Variant endpoints: the variant is addressed by URL (404 when unknown) and the product counts as updated. */
    @Transactional
    public UUID upsertVariant(UUID productId, VariantRequest variant) {
        requireProductExists(productId);
        support.requireFound(variant.id() == null || repository.findVariantById(variant.id(), productId) != null,
                ErrorCode.VARIANT_NOT_FOUND, "Variant not found");
        UUID id = saveVariant(productId, variant);
        repository.touchProduct(productId, support.adminId());
        return id;
    }

    @Transactional
    public void deleteVariant(UUID productId, UUID variantId) {
        requireProductExists(productId);
        ProductVariant existing = repository.findVariantById(variantId, productId);
        support.requireFound(existing != null, ErrorCode.VARIANT_NOT_FOUND, "Variant not found");
        repository.softDeleteVariant(variantId, productId);
        repository.touchProduct(productId, support.adminId());
        fileReferenceService.releaseFile(existing.imageFileId());
    }

    /** Inserts or updates one variant; an unknown variant id in a product payload is a bad request. */
    private UUID saveVariant(UUID productId, VariantRequest variant) {
        ProductVariant existing = variant.id() == null ? null : repository.findVariantById(variant.id(), productId);
        support.activateFile(variant.imageFileId());
        UUID id = variant.id() == null ? UUID.randomUUID() : variant.id();
        String status = support.normalizeStatusDefault(variant.status(), EcommerceSupport.ACTIVE);
        ColorValue color = resolveColor(variant);
        if (variant.id() == null) {
            repository.insertVariant(id, productId, support.trimRequired(variant.name(), "Variant name"),
                    color.id(), variant.imageFileId(), status, support.nz(variant.sortOrder()));
        } else {
            support.require(existing != null, ErrorCode.VARIANT_NOT_FOUND, "Variant not found");
            repository.updateVariant(id, productId, support.trimRequired(variant.name(), "Variant name"),
                    color.id(), variant.imageFileId(), status, support.nz(variant.sortOrder()));
            fileReferenceService.releaseFile(existing.imageFileId());
        }
        return id;
    }

    /** SKU endpoints: the SKU is addressed by URL (404 when unknown) and the product counts as updated. */
    @Transactional
    public UUID upsertSku(UUID productId, SkuRequest sku) {
        requireProductExists(productId);
        support.requireFound(sku.id() == null || repository.findSkuById(sku.id(), productId) != null,
                ErrorCode.SKU_NOT_FOUND, "SKU not found");
        UUID id = saveSku(productId, sku, Map.of());
        repository.touchProduct(productId, support.adminId());
        return id;
    }

    @Transactional
    public void deleteSku(UUID productId, UUID skuId) {
        requireProductExists(productId);
        support.requireFound(repository.findSkuById(skuId, productId) != null, ErrorCode.SKU_NOT_FOUND, "SKU not found");
        repository.softDeleteSku(skuId, productId);
        repository.touchProduct(productId, support.adminId());
    }

    /** Inserts or updates one SKU; unknown variant or SKU ids in a product payload are bad requests. */
    private UUID saveSku(UUID productId, SkuRequest sku, Map<UUID, UUID> clientVariantIds) {
        UUID variantId = sku.variantId();
        if (variantId == null && sku.variantClientId() != null) variantId = clientVariantIds.get(sku.variantClientId());
        if (variantId != null) {
            support.require(repository.findVariantById(variantId, productId) != null, ErrorCode.VARIANT_NOT_FOUND, "Variant not found");
        }
        if (sku.salePrice() != null && sku.salePrice().compareTo(sku.price()) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "salePrice must be <= price");
        }
        UUID id = sku.id() == null ? UUID.randomUUID() : sku.id();
        String skuCode = support.trimRequired(sku.skuCode(), "SKU code");
        SizeValue size = resolveSize(sku);
        support.requireUnique(!repository.skuCodeExists(skuCode, sku.id()), ErrorCode.SKU_CODE_ALREADY_EXISTS, "SKU code already exists");
        String status = support.normalizeStatusDefault(sku.status(), EcommerceSupport.ACTIVE);
        if (sku.id() == null) {
            repository.insertSku(id, productId, variantId, skuCode, size.id(), sku.price(), sku.salePrice(), sku.stock(), status);
        } else {
            support.require(repository.findSkuById(id, productId) != null, ErrorCode.SKU_NOT_FOUND, "SKU not found");
            repository.updateSku(id, productId, variantId, skuCode, size.id(), sku.price(), sku.salePrice(), sku.stock(), status);
        }
        return id;
    }

    private List<UUID> normalizeUuidFilters(List<UUID> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).distinct().toList();
    }

    private List<String> normalizeGenderFilters(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(support::normalizeProductGenderNullable)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<String> normalizeTextFilters(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(support::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<UUID> fileIds(Collection<ProductImage> images) {
        return images.stream().map(ProductImage::fileId).toList();
    }

    private List<UUID> variantImageFileIds(Collection<ProductVariant> variants) {
        return variants.stream().map(ProductVariant::imageFileId).toList();
    }

    private String uniqueSlug(String slug, String name, UUID exclude) {
        String normalized = support.uniqueSlug(slug, name);
        support.requireUnique(!repository.slugExists(normalized, exclude), ErrorCode.SLUG_ALREADY_EXISTS, "Product slug already exists");
        return normalized;
    }

    private void requireProductExists(UUID id) {
        support.requireFound(repository.existsLive(id), ErrorCode.PRODUCT_NOT_FOUND, "Product not found");
    }

    private void requireCategoryExists(UUID categoryId) {
        support.require(categoryRepository.findCategoryById(categoryId) != null, ErrorCode.CATEGORY_NOT_FOUND, "Category not found");
    }

    private void requireBrandExists(UUID brandId) {
        if (brandId == null) return;
        support.require(brandRepository.findBrandById(brandId) != null, ErrorCode.BRAND_NOT_FOUND, "Brand not found");
    }

    private SizeValue resolveSize(SkuRequest sku) {
        SizeOption size;
        if (sku.sizeId() != null) {
            size = sizeColorRepository.findSizeById(sku.sizeId());
            support.require(size != null, ErrorCode.SIZE_NOT_FOUND, "Size not found");
        } else {
            size = sizeColorRepository.findSizeByValue(DEFAULT_SIZE_VALUE);
            support.require(size != null, ErrorCode.DEFAULT_SIZE_NOT_CONFIGURED, "Default size is not configured");
        }
        support.require(EcommerceSupport.ACTIVE.equals(size.status()), ErrorCode.SIZE_INACTIVE, "Size is inactive");
        return new SizeValue(size.id(), size.value());
    }

    private ColorValue resolveColor(VariantRequest variant) {
        support.require(variant.colorId() != null, ErrorCode.VALIDATION_ERROR, "Color is required");
        ColorOption color = sizeColorRepository.findColorById(variant.colorId());
        support.require(color != null, ErrorCode.COLOR_NOT_FOUND, "Color not found");
        support.require(EcommerceSupport.ACTIVE.equals(color.status()), ErrorCode.COLOR_INACTIVE, "Color is inactive");
        return new ColorValue(color.id(), color.value(), color.colorCode());
    }

    private ProductSummary withProductUrl(ProductSummary product) {
        return product == null ? null : new ProductSummary(product.id(), product.categoryId(),
                product.categoryName(), product.brandId(), product.brandName(), product.brandSlug(), support.publicUrl(product.brandSizeGuideUrl()),
                product.gender(), product.name(), product.slug(), product.shortDescription(), product.status(),
                product.isFeatured(), product.featuredOrder(), product.minPrice(), product.minSalePrice(), product.totalStock(),
                product.primaryFileId(), support.publicUrl(product.primaryImageUrl()), product.createdAt(), product.updatedAt());
    }

    private ProductImage withImageUrl(ProductImage image) {
        return image == null || image.imageUrl() == null ? image : new ProductImage(image.id(), image.productId(), image.fileId(),
                support.publicUrl(image.imageUrl()), image.altText(), image.imageType(), image.sortOrder(), image.isPrimary());
    }

    private ProductVariant withVariantUrl(ProductVariant variant) {
        return variant == null || variant.imageUrl() == null ? variant : new ProductVariant(variant.id(), variant.productId(),
                variant.name(), variant.colorId(), variant.colorName(), variant.colorCode(), variant.imageFileId(), support.publicUrl(variant.imageUrl()),
                variant.status(), variant.sortOrder());
    }

    private record SizeValue(UUID id, String value) {}
    private record ColorValue(UUID id, String name, String colorCode) {}
    private record ContentPayload(String html) {}
}
