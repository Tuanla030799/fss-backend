package com.fss.backend.catalog.brand;

import com.fss.backend.common.ApiResponse;
import com.fss.backend.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BrandController {
    private final BrandService service;

    public BrandController(BrandService service) {
        this.service = service;
    }

    @GetMapping("/brands")
    public ApiResponse<PageResult<Brand>> publicBrands(@RequestParam(required = false) String keyword,
                                                       @RequestParam(defaultValue = "1") @Min(1) int page,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listPublicBrands(keyword, page, limit));
    }

    @GetMapping("/brands/{slug}")
    public ApiResponse<Brand> publicBrandDetail(@PathVariable String slug) {
        return ApiResponse.ok("OK", service.getPublicBrand(slug));
    }

    @GetMapping("/admin/brands")
    public ApiResponse<PageResult<Brand>> adminBrands(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") @Min(1) int page,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listAdminBrands(status, keyword, page, limit));
    }

    @GetMapping("/admin/brands/{id}")
    public ApiResponse<Brand> adminBrandDetail(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.getAdminBrand(id));
    }

    @PostMapping("/admin/brands")
    public ApiResponse<Map<String, UUID>> createBrand(@Valid @RequestBody BrandRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createBrand(body)));
    }

    @PutMapping("/admin/brands/{id}")
    public ApiResponse<Void> updateBrand(@PathVariable UUID id, @Valid @RequestBody BrandRequest body) {
        service.updateBrand(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/brands/{id}")
    public ApiResponse<Void> deleteBrand(@PathVariable UUID id) {
        service.deleteBrand(id);
        return ApiResponse.ok("Deleted", null);
    }
}
