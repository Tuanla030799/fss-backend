package com.fss.backend.promotion.coupon;

import com.fss.backend.common.ApiException;
import com.fss.backend.common.ErrorCode;
import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CouponService {
    private static final String USAGE_LIMIT_REACHED = "Mã giảm giá đã hết lượt sử dụng.";

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
        support.requireUnique(!repository.codeExists(request.code(), null), ErrorCode.COUPON_CODE_ALREADY_EXISTS, "Coupon code already exists");
        repository.insertCoupon(id, request.code(), request.name(), support.discountType(request.discountType()), request.discountValue(),
                request.maxDiscount(), request.minOrderAmount(), request.usageLimit(), request.startsAt(), request.endsAt(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.adminId());
        return id;
    }

    @Transactional
    public void updateCoupon(UUID id, CouponRequest request) {
        support.requireFound(repository.findCouponById(id) != null, ErrorCode.COUPON_NOT_FOUND, "Coupon not found");
        support.requireUnique(!repository.codeExists(request.code(), id), ErrorCode.COUPON_CODE_ALREADY_EXISTS, "Coupon code already exists");
        repository.updateCoupon(id, request.code(), request.name(), support.discountType(request.discountType()), request.discountValue(),
                request.maxDiscount(), request.minOrderAmount(), request.usageLimit(), request.startsAt(), request.endsAt(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.adminId());
    }

    @Transactional
    public void deleteCoupon(UUID id) {
        support.requireFound(repository.findCouponById(id) != null, ErrorCode.COUPON_NOT_FOUND, "Coupon not found");
        repository.softDeleteCoupon(id, support.adminId());
    }

    /** Checkout preview: an unusable code is answered with {@code valid=false} and the reason, never with an error. */
    public ValidateCouponResponse validateCoupon(ValidateCouponRequest request) {
        BigDecimal subtotal = request.subtotalAmount();
        Coupon coupon = findByCode(request.code());
        Rejection rejection = rejection(coupon, subtotal);
        if (rejection != null) return ValidateCouponResponse.invalid(rejection, subtotal);
        BigDecimal discount = calculateDiscount(coupon, subtotal);
        return ValidateCouponResponse.valid(coupon, discount, subtotal.subtract(discount).max(BigDecimal.ZERO));
    }

    /** The coupon to apply to an order; fails with the same code and message that {@link #validateCoupon} reports. */
    public Coupon requireValidCoupon(String code, BigDecimal subtotal) {
        Coupon coupon = findByCode(code);
        Rejection rejection = rejection(coupon, subtotal);
        if (rejection != null) throw new ApiException(rejection.code(), rejection.message());
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
        support.require(repository.tryConsumeUsage(id), ErrorCode.COUPON_USAGE_LIMIT_REACHED, USAGE_LIMIT_REACHED);
    }

    private Coupon findByCode(String code) {
        // Customers often paste codes with surrounding spaces; the lookup itself is case-insensitive.
        return code == null ? null : repository.findCouponByCode(code.trim());
    }

    /** Why the coupon cannot be used for this subtotal (messages are shown to customers), or null if it can. */
    private Rejection rejection(Coupon coupon, BigDecimal subtotal) {
        if (coupon == null) return new Rejection(ErrorCode.COUPON_NOT_FOUND, "Mã giảm giá không tồn tại.");
        if (!EcommerceSupport.ACTIVE.equals(coupon.status())) {
            return new Rejection(ErrorCode.COUPON_INVALID, "Mã giảm giá không còn hiệu lực.");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (coupon.startsAt() != null && coupon.startsAt().isAfter(now)) {
            return new Rejection(ErrorCode.COUPON_NOT_STARTED, "Mã giảm giá chưa đến thời gian áp dụng.");
        }
        if (coupon.endsAt() != null && coupon.endsAt().isBefore(now)) {
            return new Rejection(ErrorCode.COUPON_EXPIRED, "Mã giảm giá đã hết hạn.");
        }
        if (coupon.usageLimit() != null && coupon.usedCount() >= coupon.usageLimit()) {
            return new Rejection(ErrorCode.COUPON_USAGE_LIMIT_REACHED, USAGE_LIMIT_REACHED);
        }
        if (coupon.minOrderAmount() != null && subtotal.compareTo(coupon.minOrderAmount()) < 0) {
            return new Rejection(ErrorCode.COUPON_MIN_SUBTOTAL,
                    "Đơn hàng cần tối thiểu " + formatVnd(coupon.minOrderAmount()) + " để dùng mã này.");
        }
        return null;
    }

    /** Vietnamese money format: 500000.00 becomes "500.000 đ". */
    static String formatVnd(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.##", symbols).format(amount) + " đ";
    }

    record Rejection(ErrorCode code, String message) {}
}
