package com.fss.backend.catalog.product;

import com.fss.backend.common.ApiResponse;
import com.fss.backend.shared.UpdateStatusRequest;
import com.fss.backend.catalog.sku.SkuRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/products")
    public ApiResponse<List<ProductSummary>> publicProducts(@RequestParam(required = false) UUID categoryId,
                                                            @RequestParam(required = false) String categorySlug,
                                                            @RequestParam(required = false) UUID brandId,
                                                            @RequestParam(required = false) String brandSlug,
                                                            @RequestParam(required = false) String gender,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String size,
                                                            @RequestParam(required = false) String color,
                                                            @RequestParam(required = false) BigDecimal minPrice,
                                                            @RequestParam(required = false) BigDecimal maxPrice,
                                                            @RequestParam(defaultValue = "1") @Min(1) int page,
                                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listPublicProducts(categoryId, categorySlug, brandId, brandSlug, gender, keyword, size, color, minPrice, maxPrice, page, limit));
    }

    @GetMapping("/products/featured")
    public ApiResponse<List<ProductSummary>> featuredProducts(@RequestParam(defaultValue = "12") @Min(1) @Max(30) int limit) {
        return ApiResponse.ok("OK", service.featuredProducts(limit));
    }

    @GetMapping("/products/{slug}")
    public ApiResponse<ProductDetail> productDetail(@PathVariable String slug) {
        return ApiResponse.ok("OK", service.getPublicProduct(slug));
    }

    @GetMapping("/admin/products")
    public ApiResponse<List<ProductSummary>> adminProducts(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) UUID categoryId,
                                                           @RequestParam(required = false) UUID brandId,
                                                           @RequestParam(required = false) String gender,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "1") @Min(1) int page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listAdminProducts(status, categoryId, brandId, gender, keyword, page, limit));
    }

    @GetMapping("/admin/products/{id}")
    public ApiResponse<ProductDetail> adminProductDetail(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.getAdminProduct(id));
    }

    @PostMapping("/admin/products")
    public ApiResponse<Map<String, UUID>> createProduct(@Valid @RequestBody ProductRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createProduct(body)));
    }

    @PutMapping("/admin/products/{id}")
    public ApiResponse<Void> updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest body) {
        service.updateProduct(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @PatchMapping("/admin/products/{id}/status")
    public ApiResponse<Void> updateProductStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest body) {
        service.updateProductStatus(id, body.status());
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/products/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable UUID id) {
        service.deleteProduct(id);
        return ApiResponse.ok("Deleted", null);
    }
    @PutMapping("/admin/products/{productId}/variants/{variantId}")
    public ApiResponse<Map<String, UUID>> upsertVariant(@PathVariable UUID productId,
                                                        @PathVariable UUID variantId,
                                                        @Valid @RequestBody VariantRequest body) {
        UUID id = service.upsertVariant(productId, new VariantRequest(variantId, body.clientId(), body.name(), body.colorName(),
                body.colorCode(), body.imageFileId(), body.status(), body.sortOrder()));
        return ApiResponse.ok("Updated", Map.of("id", id));
    }

    @PostMapping("/admin/products/{productId}/variants")
    public ApiResponse<Map<String, UUID>> createVariant(@PathVariable UUID productId,
                                                        @Valid @RequestBody VariantRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.upsertVariant(productId, body)));
    }

    @DeleteMapping("/admin/products/{productId}/variants/{variantId}")
    public ApiResponse<Void> deleteVariant(@PathVariable UUID productId, @PathVariable UUID variantId) {
        service.deleteVariant(productId, variantId);
        return ApiResponse.ok("Deleted", null);
    }

    @PostMapping("/admin/products/{productId}/skus")
    public ApiResponse<Map<String, UUID>> createSku(@PathVariable UUID productId, @Valid @RequestBody SkuRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.upsertSku(productId, body)));
    }

    @PutMapping("/admin/products/{productId}/skus/{skuId}")
    public ApiResponse<Map<String, UUID>> updateSku(@PathVariable UUID productId,
                                                    @PathVariable UUID skuId,
                                                    @Valid @RequestBody SkuRequest body) {
        UUID id = service.upsertSku(productId, new SkuRequest(skuId, body.variantClientId(), body.variantId(), body.skuCode(),
                body.size(), body.price(), body.salePrice(), body.stock(), body.status()));
        return ApiResponse.ok("Updated", Map.of("id", id));
    }

    @DeleteMapping("/admin/products/{productId}/skus/{skuId}")
    public ApiResponse<Void> deleteSku(@PathVariable UUID productId, @PathVariable UUID skuId) {
        service.deleteSku(productId, skuId);
        return ApiResponse.ok("Deleted", null);
    }

}
