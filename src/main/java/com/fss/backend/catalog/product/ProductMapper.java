package com.fss.backend.catalog.product;

import com.fss.backend.catalog.sku.Sku;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ProductMapper {
    List<ProductSummary> listProducts(@Param("publicOnly") boolean publicOnly, @Param("status") String status,
                                      @Param("categoryIds") List<UUID> categoryIds, @Param("categorySlug") String categorySlug,
                                      @Param("brandIds") List<UUID> brandIds, @Param("brandSlug") String brandSlug,
                                      @Param("genders") List<String> genders,
                                      @Param("keyword") String keyword, @Param("sizes") List<String> sizes,
                                      @Param("colors") List<String> colors, @Param("minPrice") BigDecimal minPrice,
                                      @Param("maxPrice") BigDecimal maxPrice, @Param("featuredOnly") boolean featuredOnly,
                                      @Param("limit") int limit, @Param("offset") int offset);
    long countProducts(@Param("publicOnly") boolean publicOnly, @Param("status") String status,
                       @Param("categoryIds") List<UUID> categoryIds, @Param("categorySlug") String categorySlug,
                       @Param("brandIds") List<UUID> brandIds, @Param("brandSlug") String brandSlug,
                       @Param("genders") List<String> genders,
                       @Param("keyword") String keyword, @Param("sizes") List<String> sizes,
                       @Param("colors") List<String> colors, @Param("minPrice") BigDecimal minPrice,
                       @Param("maxPrice") BigDecimal maxPrice, @Param("featuredOnly") boolean featuredOnly);
    ProductSummary findProductSummaryById(@Param("id") UUID id);
    String findProductDescriptionJson(@Param("id") UUID id);
    String findProductDescriptionHtml(@Param("id") UUID id);
    ProductSummary findProductSummaryBySlug(@Param("slug") String slug, @Param("publicOnly") boolean publicOnly);
    int countProductSlug(@Param("slug") String slug, @Param("excludeId") UUID excludeId);
    void insertProduct(@Param("id") UUID id, @Param("categoryId") UUID categoryId, @Param("brandId") UUID brandId,
                       @Param("gender") String gender, @Param("name") String name,
                       @Param("slug") String slug, @Param("shortDescription") String shortDescription,
                       @Param("descriptionJson") String descriptionJson, @Param("descriptionHtml") String descriptionHtml,
                       @Param("status") String status,
                       @Param("isFeatured") Boolean isFeatured, @Param("featuredOrder") Integer featuredOrder,
                       @Param("adminId") UUID adminId);
    void updateProduct(@Param("id") UUID id, @Param("categoryId") UUID categoryId, @Param("brandId") UUID brandId,
                       @Param("gender") String gender, @Param("name") String name,
                       @Param("slug") String slug, @Param("shortDescription") String shortDescription,
                       @Param("descriptionJson") String descriptionJson, @Param("descriptionHtml") String descriptionHtml,
                       @Param("status") String status,
                       @Param("isFeatured") Boolean isFeatured, @Param("featuredOrder") Integer featuredOrder,
                       @Param("adminId") UUID adminId);
    void updateProductStatus(@Param("id") UUID id, @Param("status") String status, @Param("adminId") UUID adminId);
    void softDeleteProduct(@Param("id") UUID id, @Param("adminId") UUID adminId);
    List<ProductImage> listImages(@Param("productId") UUID productId);
    void deleteImages(@Param("productId") UUID productId);
    void insertImage(@Param("id") UUID id, @Param("productId") UUID productId, @Param("fileId") UUID fileId,
                     @Param("altText") String altText, @Param("imageType") String imageType,
                     @Param("sortOrder") Integer sortOrder, @Param("isPrimary") Boolean isPrimary);
    List<ProductVariant> listVariants(@Param("productId") UUID productId);
    void deleteVariants(@Param("productId") UUID productId);
    void softDeleteMissingVariants(@Param("productId") UUID productId, @Param("keepIds") Set<String> keepIds);
    ProductVariant findVariantById(@Param("id") UUID id, @Param("productId") UUID productId);
    void insertVariant(@Param("id") UUID id, @Param("productId") UUID productId, @Param("name") String name,
                       @Param("colorId") UUID colorId,
                       @Param("imageFileId") UUID imageFileId, @Param("status") String status,
                       @Param("sortOrder") Integer sortOrder);
    void updateVariant(@Param("id") UUID id, @Param("productId") UUID productId, @Param("name") String name,
                       @Param("colorId") UUID colorId,
                       @Param("imageFileId") UUID imageFileId, @Param("status") String status,
                       @Param("sortOrder") Integer sortOrder);
    void softDeleteVariant(@Param("id") UUID id, @Param("productId") UUID productId);
    List<Sku> listSkus(@Param("productId") UUID productId);
    void deleteSkus(@Param("productId") UUID productId);
    void softDeleteMissingSkus(@Param("productId") UUID productId, @Param("keepIds") Set<String> keepIds);
    Sku findSkuById(@Param("id") UUID id, @Param("productId") UUID productId);
    int countSkuCode(@Param("skuCode") String skuCode, @Param("excludeId") UUID excludeId);
    void insertSku(@Param("id") UUID id, @Param("productId") UUID productId, @Param("variantId") UUID variantId,
                   @Param("skuCode") String skuCode, @Param("sizeId") UUID sizeId, @Param("price") BigDecimal price,
                   @Param("salePrice") BigDecimal salePrice, @Param("stock") Integer stock, @Param("status") String status);
    void updateSku(@Param("id") UUID id, @Param("productId") UUID productId, @Param("variantId") UUID variantId,
                   @Param("skuCode") String skuCode, @Param("sizeId") UUID sizeId, @Param("price") BigDecimal price,
                   @Param("salePrice") BigDecimal salePrice, @Param("stock") Integer stock, @Param("status") String status);
    void softDeleteSku(@Param("id") UUID id, @Param("productId") UUID productId);
}
