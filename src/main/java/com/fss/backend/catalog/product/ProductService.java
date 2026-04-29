package com.fss.backend.catalog.product;

import com.fss.backend.common.ApiException;
import com.fss.backend.catalog.category.CategoryMapper;
import com.fss.backend.catalog.sku.Sku;
import com.fss.backend.catalog.sku.SkuRequest;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductService {
    private final ProductMapper mapper;
    private final CategoryMapper categoryMapper;
    private final EcommerceSupport support;

    public ProductService(ProductMapper mapper, CategoryMapper categoryMapper, EcommerceSupport support) {
        this.mapper = mapper;
        this.categoryMapper = categoryMapper;
        this.support = support;
    }

    public List<ProductSummary> listPublicProducts(UUID categoryId, String categorySlug, String keyword, String size,
                                                   String color, BigDecimal minPrice, BigDecimal maxPrice,
                                                   int page, int limit) {
        return mapper.listProducts(true, null, categoryId, categorySlug, keyword, size, color, minPrice, maxPrice,
                false, support.safeLimit(limit), support.offset(page, limit)).stream().map(this::withProductUrl).toList();
    }

    public List<ProductSummary> listAdminProducts(String status, UUID categoryId, String keyword, int page, int limit) {
        return mapper.listProducts(false, support.normalizeProductStatusNullable(status), categoryId, null, keyword,
                null, null, null, null, false, support.safeLimit(limit), support.offset(page, limit))
                .stream().map(this::withProductUrl).toList();
    }

    @Cacheable(value = "featuredProducts", key = "#limit")
    public List<ProductSummary> featuredProducts(int limit) {
        return mapper.listProducts(true, null, null, null, null, null, null, null, null, true,
                Math.min(Math.max(limit, 1), 30), 0).stream().map(this::withProductUrl).toList();
    }

    public ProductDetail getPublicProduct(String slug) {
        return productDetail(mapper.findProductSummaryBySlug(slug, true));
    }

    public ProductDetail getAdminProduct(UUID id) {
        return productDetail(mapper.findProductSummaryById(id));
    }

    @Transactional
    @CacheEvict(value = "featuredProducts", allEntries = true)
    public UUID createProduct(ProductRequest request) {
        support.require(categoryMapper.findCategoryById(request.categoryId()) != null, "Category not found");
        UUID id = UUID.randomUUID();
        mapper.insertProduct(id, request.categoryId(), request.name().trim(), uniqueSlug(request.slug(), request.name(), null),
                request.shortDescription(), support.json(request.descriptionJson()), support.normalizeProductStatusDefault(request.status()),
                support.bool(request.isFeatured()), support.nz(request.featuredOrder()), support.adminId());
        replaceProductChildren(id, request.images(), request.variants(), request.skus());
        return id;
    }

    @Transactional
    @CacheEvict(value = "featuredProducts", allEntries = true)
    public void updateProduct(UUID id, ProductRequest request) {
        support.require(mapper.findProductSummaryById(id) != null, "Product not found");
        support.require(categoryMapper.findCategoryById(request.categoryId()) != null, "Category not found");
        mapper.updateProduct(id, request.categoryId(), request.name().trim(), uniqueSlug(request.slug(), request.name(), id),
                request.shortDescription(), support.json(request.descriptionJson()), support.normalizeProductStatusDefault(request.status()),
                support.bool(request.isFeatured()), support.nz(request.featuredOrder()), support.adminId());
        replaceProductChildren(id, request.images(), request.variants(), request.skus());
    }

    @Transactional
    @CacheEvict(value = "featuredProducts", allEntries = true)
    public void updateProductStatus(UUID id, String status) {
        support.require(mapper.findProductSummaryById(id) != null, "Product not found");
        mapper.updateProductStatus(id, support.normalizeProductStatusRequired(status), support.adminId());
    }

    @Transactional
    @CacheEvict(value = "featuredProducts", allEntries = true)
    public void deleteProduct(UUID id) {
        support.require(mapper.findProductSummaryById(id) != null, "Product not found");
        mapper.softDeleteProduct(id, support.adminId());
    }

    private ProductDetail productDetail(ProductSummary summary) {
        support.require(summary != null, "Product not found");
        List<ProductImage> images = mapper.listImages(summary.id()).stream().map(this::withImageUrl).toList();
        List<ProductVariant> variants = mapper.listVariants(summary.id()).stream().map(this::withVariantUrl).toList();
        List<Sku> skus = mapper.listSkus(summary.id());
        return new ProductDetail(summary.id(), summary.categoryId(), summary.categoryName(), summary.name(), summary.slug(),
                summary.shortDescription(), loadDescriptionJson(summary.id()), summary.status(), summary.isFeatured(),
                summary.featuredOrder(), summary.createdAt(), images, variants, skus);
    }

    private String loadDescriptionJson(UUID productId) {
        String json = mapper.findProductDescriptionJson(productId);
        return json == null || json.isBlank() ? "{}" : json;
    }

    private void replaceProductChildren(UUID productId, List<ProductImageRequest> images, List<VariantRequest> variants, List<SkuRequest> skus) {
        mapper.deleteImages(productId);
        if (images != null) {
            for (ProductImageRequest image : images) {
                support.activateFile(image.fileId());
                mapper.insertImage(UUID.randomUUID(), productId, image.fileId(), image.altText(),
                        support.def(image.imageType(), "GALLERY"), support.nz(image.sortOrder()), support.bool(image.isPrimary()));
            }
        }

        Map<UUID, UUID> clientVariantIds = new HashMap<>();
        java.util.Set<UUID> keepVariantIds = new java.util.HashSet<>();
        if (variants != null) {
            for (VariantRequest variant : variants) {
                UUID id = upsertVariant(productId, variant);
                keepVariantIds.add(id);
                if (variant.clientId() != null) clientVariantIds.put(variant.clientId(), id);
            }
        }

        java.util.Set<UUID> keepSkuIds = new java.util.HashSet<>();
        if (skus != null) {
            for (SkuRequest sku : skus) {
                UUID id = upsertSku(productId, sku, clientVariantIds);
                keepSkuIds.add(id);
            }
        }
        mapper.softDeleteMissingSkus(productId, keepSkuIds);
        mapper.softDeleteMissingVariants(productId, keepVariantIds);
    }

    public UUID upsertVariant(UUID productId, VariantRequest variant) {
        support.require(mapper.findProductSummaryById(productId) != null, "Product not found");
        support.activateFile(variant.imageFileId());
        UUID id = variant.id() == null ? UUID.randomUUID() : variant.id();
        String status = support.normalizeStatusDefault(variant.status(), EcommerceSupport.ACTIVE);
        if (variant.id() == null) {
            mapper.insertVariant(id, productId, variant.name(), variant.colorName(), variant.colorCode(), variant.imageFileId(), status, support.nz(variant.sortOrder()));
        } else {
            support.require(mapper.findVariantById(id, productId) != null, "Variant not found");
            mapper.updateVariant(id, productId, variant.name(), variant.colorName(), variant.colorCode(), variant.imageFileId(), status, support.nz(variant.sortOrder()));
        }
        return id;
    }

    public void deleteVariant(UUID productId, UUID variantId) {
        support.require(mapper.findVariantById(variantId, productId) != null, "Variant not found");
        mapper.softDeleteVariant(variantId, productId);
    }

    public UUID upsertSku(UUID productId, SkuRequest sku, Map<UUID, UUID> clientVariantIds) {
        support.require(mapper.findProductSummaryById(productId) != null, "Product not found");
        UUID variantId = sku.variantId() != null ? sku.variantId() : clientVariantIds.get(sku.variantClientId());
        if (variantId != null) {
            support.require(mapper.findVariantById(variantId, productId) != null, "Variant not found");
        }
        if (sku.salePrice() != null && sku.salePrice().compareTo(sku.price()) > 0) {
            throw new ApiException("salePrice must be <= price");
        }
        UUID id = sku.id() == null ? UUID.randomUUID() : sku.id();
        support.require(mapper.countSkuCode(sku.skuCode(), sku.id()) == 0, "SKU code already exists");
        String status = support.normalizeStatusDefault(sku.status(), EcommerceSupport.ACTIVE);
        if (sku.id() == null) {
            mapper.insertSku(id, productId, variantId, sku.skuCode(), sku.size(), sku.price(), sku.salePrice(), sku.stock(), status);
        } else {
            support.require(mapper.findSkuById(id, productId) != null, "SKU not found");
            mapper.updateSku(id, productId, variantId, sku.skuCode(), sku.size(), sku.price(), sku.salePrice(), sku.stock(), status);
        }
        return id;
    }

    public UUID upsertSku(UUID productId, SkuRequest sku) {
        return upsertSku(productId, sku, Map.of());
    }

    public void deleteSku(UUID productId, UUID skuId) {
        support.require(mapper.findSkuById(skuId, productId) != null, "SKU not found");
        mapper.softDeleteSku(skuId, productId);
    }

    private String uniqueSlug(String slug, String name, UUID exclude) {
        String normalized = support.uniqueSlug(slug, name);
        support.require(mapper.countProductSlug(normalized, exclude) == 0, "Product slug already exists");
        return normalized;
    }

    private ProductSummary withProductUrl(ProductSummary product) {
        return product == null || product.primaryImageUrl() == null ? product : new ProductSummary(product.id(), product.categoryId(),
                product.categoryName(), product.name(), product.slug(), product.shortDescription(), product.status(),
                product.isFeatured(), product.featuredOrder(), product.minPrice(), product.minSalePrice(), product.totalStock(),
                product.primaryFileId(), support.publicUrl(product.primaryImageUrl()), product.createdAt());
    }

    private ProductImage withImageUrl(ProductImage image) {
        return image == null || image.imageUrl() == null ? image : new ProductImage(image.id(), image.productId(), image.fileId(),
                support.publicUrl(image.imageUrl()), image.altText(), image.imageType(), image.sortOrder(), image.isPrimary());
    }

    private ProductVariant withVariantUrl(ProductVariant variant) {
        return variant == null || variant.imageUrl() == null ? variant : new ProductVariant(variant.id(), variant.productId(),
                variant.name(), variant.colorName(), variant.colorCode(), variant.imageFileId(), support.publicUrl(variant.imageUrl()),
                variant.status(), variant.sortOrder());
    }
}
