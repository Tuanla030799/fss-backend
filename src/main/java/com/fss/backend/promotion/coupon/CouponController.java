package com.fss.backend.promotion.coupon;

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
public class CouponController {
    private final CouponService service;

    public CouponController(CouponService service) {
        this.service = service;
    }

    @PostMapping("/coupons/validate")
    public ApiResponse<ValidateCouponResponse> validateCoupon(@Valid @RequestBody ValidateCouponRequest body) {
        return ApiResponse.ok("OK", service.validateCoupon(body));
    }

    @GetMapping("/admin/coupons")
    public ApiResponse<PageResult<Coupon>> adminCoupons(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") @Min(1) int page,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listCoupons(status, keyword, page, limit));
    }

    @PostMapping("/admin/coupons")
    public ApiResponse<Map<String, UUID>> createCoupon(@Valid @RequestBody CouponRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createCoupon(body)));
    }

    @PutMapping("/admin/coupons/{id}")
    public ApiResponse<Void> updateCoupon(@PathVariable UUID id, @Valid @RequestBody CouponRequest body) {
        service.updateCoupon(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/coupons/{id}")
    public ApiResponse<Void> deleteCoupon(@PathVariable UUID id) {
        service.deleteCoupon(id);
        return ApiResponse.ok("Deleted", null);
    }
}
