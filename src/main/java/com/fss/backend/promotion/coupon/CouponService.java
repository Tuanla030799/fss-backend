package com.fss.backend.promotion.coupon;

import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class CouponService {
    private final CouponRepository repository;
    private final EcommerceSupport support;

    public CouponService(CouponRepository repository, EcommerceSupport support) {
        this.repository = repository;
        this.support = support;
    }

    public PageResult<Coupon> listCoupons(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<Coupon> items = repository.listCoupons(normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, repository.countCoupons(normalizedStatus, keyword));
    }

    @Transactional
    public UUID createCoupon(CouponRequest request) {
        UUID id = UUID.randomUUID();
        support.require(!repository.codeExists(request.code(), null), "Coupon code already exists");
        repository.insertCoupon(id, request.code(), request.name(), support.discountType(request.discountType()), request.discountValue(),
                request.maxDiscount(), request.minOrderAmount(), request.usageLimit(), request.startsAt(), request.endsAt(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.adminId());
        return id;
    }

    @Transactional
    public void updateCoupon(UUID id, CouponRequest request) {
        support.require(repository.findCouponById(id) != null, "Coupon not found");
        support.require(!repository.codeExists(request.code(), id), "Coupon code already exists");
        repository.updateCoupon(id, request.code(), request.name(), support.discountType(request.discountType()), request.discountValue(),
                request.maxDiscount(), request.minOrderAmount(), request.usageLimit(), request.startsAt(), request.endsAt(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.adminId());
    }

    @Transactional
    public void deleteCoupon(UUID id) {
        support.require(repository.findCouponById(id) != null, "Coupon not found");
        repository.softDeleteCoupon(id, support.adminId());
    }

    public ValidateCouponResponse validateCoupon(ValidateCouponRequest request) {
        Coupon coupon = requireValidCoupon(request.code(), request.subtotalAmount());
        BigDecimal discount = calculateDiscount(coupon, request.subtotalAmount());
        return new ValidateCouponResponse(coupon.id(), coupon.code(), discount,
                request.subtotalAmount().subtract(discount).max(BigDecimal.ZERO));
    }

    public Coupon requireValidCoupon(String code, BigDecimal subtotal) {
        Coupon coupon = repository.findCouponByCode(code);
        support.require(coupon != null && EcommerceSupport.ACTIVE.equals(coupon.status()), "Coupon is invalid");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
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

    /** Consumes one coupon use atomically; fails (and rolls back the caller's transaction) once the limit is reached. */
    public void incrementCouponUsed(UUID id) {
        support.require(repository.tryConsumeUsage(id), "Coupon usage limit reached");
    }
}
