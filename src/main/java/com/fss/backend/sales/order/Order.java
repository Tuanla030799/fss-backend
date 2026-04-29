package com.fss.backend.sales.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Order(UUID id, String orderCode, String customerName, String customerPhone, String customerEmail,
                    String shippingAddress, String note, String status, UUID couponId, UUID customerId,
                    String paymentMethod, String paymentStatus, OffsetDateTime paidAt,
                    String shippingMethod, String shippingStatus, String trackingCode, String internalNote,
                    BigDecimal subtotalAmount, BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal totalAmount,
                    OffsetDateTime createdAt) {}
