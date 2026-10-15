package com.fss.backend.promotion.coupon;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Collection {@code coupons}. {@code code} is stored upper-case; {@code usedCount} only grows via a guarded $inc. */
@Document(CouponDocument.COLLECTION)
public record CouponDocument(@Id UUID id, String code, String name, String discountType, BigDecimal discountValue,
                             BigDecimal maxDiscount, BigDecimal minOrderAmount, Integer usageLimit, Integer usedCount,
                             Instant startsAt, Instant endsAt, String status, UUID createdBy, UUID updatedBy,
                             Instant createdAt, Instant updatedAt, boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "coupons";
}
