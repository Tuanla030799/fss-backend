package com.fss.backend.content.banner;

import com.fss.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ApiResponse<List<LandingBanner>> publicBanners() {
        return ApiResponse.ok("OK", service.listPublicBanners());
    }

    @GetMapping("/admin/landing-banners")
    public ApiResponse<List<LandingBanner>> adminBanners(@RequestParam(required = false) String status) {
        return ApiResponse.ok("OK", service.listAdminBanners(status));
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
