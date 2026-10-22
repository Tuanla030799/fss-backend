package com.fss.backend.promotion.coupon;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of {@code POST /coupons/validate}. An unusable code is a normal answer, not an error: {@code valid=false}
 * with the reason in {@code code} (an {@code ErrorCode} name such as {@code COUPON_EXPIRED}) and {@code message}, no
 * discount and {@code finalAmount} = subtotal. {@code code} and {@code message} are null when the coupon is valid.
 */
public record ValidateCouponResponse(boolean valid, String code, String message, UUID couponId, String couponCode,
                                     BigDecimal discountAmount, BigDecimal finalAmount) {

    static ValidateCouponResponse valid(Coupon coupon, BigDecimal discountAmount, BigDecimal finalAmount) {
        return new ValidateCouponResponse(true, null, null, coupon.id(), coupon.code(), discountAmount, finalAmount);
    }

    static ValidateCouponResponse invalid(CouponService.Rejection rejection, BigDecimal subtotal) {
        return new ValidateCouponResponse(false, rejection.code().name(), rejection.message(), null, null,
                BigDecimal.ZERO.setScale(2), subtotal);
    }
}
