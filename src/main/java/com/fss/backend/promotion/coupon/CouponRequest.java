package com.fss.backend.promotion.coupon;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CouponRequest(@NotBlank String code, String name, @NotBlank String discountType,
                            @NotNull @DecimalMin("0.01") BigDecimal discountValue,
                            @DecimalMin("0.00") BigDecimal maxDiscount,
                            @DecimalMin("0.00") BigDecimal minOrderAmount,
                            @Min(1) Integer usageLimit, OffsetDateTime startsAt, OffsetDateTime endsAt,
                            String status) {}
