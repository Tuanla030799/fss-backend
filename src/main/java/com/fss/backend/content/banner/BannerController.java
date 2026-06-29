package com.fss.backend.content.banner;

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
public class BannerController {
    private final BannerService service;

    public BannerController(BannerService service) {
        this.service = service;
    }

    @GetMapping("/landing-banners")
    public ApiResponse<PageResult<LandingBanner>> publicBanners(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listPublicBanners(page, limit));
    }

    @GetMapping("/admin/landing-banners")
    public ApiResponse<PageResult<LandingBanner>> adminBanners(@RequestParam(required = false) String status,
                                                               @RequestParam(defaultValue = "1") @Min(1) int page,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listAdminBanners(status, page, limit));
    }

    @PostMapping("/admin/landing-banners")
    public ApiResponse<Map<String, UUID>> createBanner(@Valid @RequestBody LandingBannerRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createBanner(body)));
    }

    @PutMapping("/admin/landing-banners/{id}")
    public ApiResponse<Void> updateBanner(@PathVariable UUID id, @Valid @RequestBody LandingBannerRequest body) {
        service.updateBanner(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/landing-banners/{id}")
    public ApiResponse<Void> deleteBanner(@PathVariable UUID id) {
        service.deleteBanner(id);
        return ApiResponse.ok("Deleted", null);
    }
}
