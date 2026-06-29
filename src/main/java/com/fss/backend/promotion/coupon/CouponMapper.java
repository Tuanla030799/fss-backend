package com.fss.backend.promotion.coupon;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface CouponMapper {
    Coupon findCouponByCode(@Param("code") String code);
    Coupon findCouponById(@Param("id") UUID id);
    List<Coupon> listCoupons(@Param("status") String status, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);
    long countCoupons(@Param("status") String status, @Param("keyword") String keyword);
    void insertCoupon(@Param("id") UUID id, @Param("code") String code, @Param("name") String name,
                      @Param("discountType") String discountType, @Param("discountValue") BigDecimal discountValue,
                      @Param("maxDiscount") BigDecimal maxDiscount, @Param("minOrderAmount") BigDecimal minOrderAmount,
                      @Param("usageLimit") Integer usageLimit, @Param("startsAt") OffsetDateTime startsAt,
                      @Param("endsAt") OffsetDateTime endsAt, @Param("status") String status, @Param("adminId") UUID adminId);
    void updateCoupon(@Param("id") UUID id, @Param("code") String code, @Param("name") String name,
                      @Param("discountType") String discountType, @Param("discountValue") BigDecimal discountValue,
                      @Param("maxDiscount") BigDecimal maxDiscount, @Param("minOrderAmount") BigDecimal minOrderAmount,
                      @Param("usageLimit") Integer usageLimit, @Param("startsAt") OffsetDateTime startsAt,
                      @Param("endsAt") OffsetDateTime endsAt, @Param("status") String status, @Param("adminId") UUID adminId);
    void incrementCouponUsed(@Param("id") UUID id);
    void softDeleteCoupon(@Param("id") UUID id, @Param("adminId") UUID adminId);
}
