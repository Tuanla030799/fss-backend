package com.fss.backend.promotion.coupon;

import java.math.BigDecimal;
import java.util.UUID;

public record ValidateCouponResponse(UUID couponId, String code, BigDecimal discountAmount, BigDecimal finalAmount) {}
