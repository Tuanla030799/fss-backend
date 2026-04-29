package com.fss.backend.promotion.coupon;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ValidateCouponRequest(@NotBlank String code, @NotNull @DecimalMin("0.00") BigDecimal subtotalAmount) {}
