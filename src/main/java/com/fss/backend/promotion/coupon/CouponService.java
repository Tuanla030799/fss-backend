package com.fss.backend.promotion.coupon;

import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CouponService {
    private final CouponMapper mapper;
    private final EcommerceSupport support;

    public CouponService(CouponMapper mapper, EcommerceSupport support) {
        this.mapper = mapper;
        this.support = support;
    }

    public List<Coupon> listCoupons(String status, String keyword, int page, int limit) {
        return mapper.listCoupons(support.normalizeStatusNullable(status), keyword, support.safeLimit(limit), support.offset(page, limit));
    }

    @Transactional
    public UUID createCoupon(CouponRequest request) {
        UUID id = UUID.randomUUID();
        mapper.insertCoupon(id, request.code(), request.name(), support.discountType(request.discountType()), request.discountValue(),
                request.maxDiscount(), request.minOrderAmount(), request.usageLimit(), request.startsAt(), request.endsAt(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.adminId());
        return id;
    }

    @Transactional
    public void updateCoupon(UUID id, CouponRequest request) {
        support.require(mapper.findCouponById(id) != null, "Coupon not found");
        mapper.updateCoupon(id, request.code(), request.name(), support.discountType(request.discountType()), request.discountValue(),
                request.maxDiscount(), request.minOrderAmount(), request.usageLimit(), request.startsAt(), request.endsAt(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.adminId());
    }

    @Transactional
    public void deleteCoupon(UUID id) {
        support.require(mapper.findCouponById(id) != null, "Coupon not found");
        mapper.softDeleteCoupon(id, support.adminId());
    }

    public ValidateCouponResponse validateCoupon(ValidateCouponRequest request) {
        Coupon coupon = requireValidCoupon(request.code(), request.subtotalAmount());
        BigDecimal discount = calculateDiscount(coupon, request.subtotalAmount());
        return new ValidateCouponResponse(coupon.id(), coupon.code(), discount,
                request.subtotalAmount().subtract(discount).max(BigDecimal.ZERO));
    }

    public Coupon requireValidCoupon(String code, BigDecimal subtotal) {
        Coupon coupon = mapper.findCouponByCode(code);
        support.require(coupon != null && EcommerceSupport.ACTIVE.equals(coupon.status()), "Coupon is invalid");
        OffsetDateTime now = OffsetDateTime.now();
        support.require(coupon.startsAt() == null || !coupon.startsAt().isAfter(now), "Coupon has not started");
        support.require(coupon.endsAt() == null || !coupon.endsAt().isBefore(now), "Coupon has expired");
        support.require(coupon.usageLimit() == null || coupon.usedCount() < coupon.usageLimit(), "Coupon usage limit reached");
        support.require(coupon.minOrderAmount() == null || subtotal.compareTo(coupon.minOrderAmount()) >= 0,
                "Order amount is below coupon minimum");
        return coupon;
    }

    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount = "PERCENT".equals(coupon.discountType())
                ? subtotal.multiply(coupon.discountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : coupon.discountValue();
        if (coupon.maxDiscount() != null) discount = discount.min(coupon.maxDiscount());
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    public void incrementCouponUsed(UUID id) {
        mapper.incrementCouponUsed(id);
    }
}
