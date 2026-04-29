package com.fss.backend.promotion.coupon;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Coupon(UUID id, String code, String name, String discountType, BigDecimal discountValue,
                     BigDecimal maxDiscount, BigDecimal minOrderAmount, Integer usageLimit, Integer usedCount,
                     OffsetDateTime startsAt, OffsetDateTime endsAt, String status, OffsetDateTime createdAt) {}
