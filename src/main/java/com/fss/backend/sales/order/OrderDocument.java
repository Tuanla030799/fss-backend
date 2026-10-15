package com.fss.backend.sales.order;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Collection {@code orders}. Line items (immutable snapshots) and the status history are embedded, so a status change
 * and its history entry are written by one atomic single-document update. Money is Decimal128 with scale 2.
 */
@Document(OrderDocument.COLLECTION)
public record OrderDocument(@Id UUID id, String orderCode, String customerName, String customerPhone, String customerEmail,
                            String shippingAddress, String note, String status, UUID couponId, UUID customerId,
                            String paymentMethod, String paymentStatus, Instant paidAt, String shippingMethod,
                            String shippingStatus, String trackingCode, String internalNote,
                            BigDecimal subtotalAmount, BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal totalAmount,
                            List<Item> items, List<History> statusHistory, Instant createdAt, Instant updatedAt,
                            boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "orders";

    public record Item(UUID id, UUID skuId, String productName, String skuCode, String variantName, String size,
                       BigDecimal unitPrice, Integer quantity, BigDecimal lineTotal, Instant createdAt) {}

    public record History(UUID id, String oldStatus, String newStatus, String note, UUID createdBy, Instant createdAt) {}
}
