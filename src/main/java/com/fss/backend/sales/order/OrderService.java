package com.fss.backend.sales.order;

import com.fss.backend.promotion.coupon.Coupon;
import com.fss.backend.promotion.coupon.CouponService;
import com.fss.backend.catalog.sku.SkuForOrder;
import com.fss.backend.common.ErrorCode;
import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import com.fss.backend.shared.mongo.MongoTransactionRunner;
import com.fss.backend.shared.mongo.MongoValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository repository;
    private final CouponService couponService;
    private final EcommerceSupport support;
    private final MongoTransactionRunner transactions;

    public OrderService(OrderRepository repository, CouponService couponService, EcommerceSupport support,
                        MongoTransactionRunner transactions) {
        this.repository = repository;
        this.couponService = couponService;
        this.support = support;
        this.transactions = transactions;
    }

    /**
     * Creates the order, decrements stock and consumes the coupon in one MongoDB transaction:
     * <ul>
     *   <li>each SKU is decremented with a conditional update ({@code stock >= quantity}); if any fails the whole
     *       transaction aborts, so no stock, coupon use or order is persisted;</li>
     *   <li>the coupon use is a guarded {@code $inc} that cannot exceed {@code usageLimit};</li>
     *   <li>concurrent orders on the same SKU raise a write conflict ({@code TransientTransactionError}); the runner
     *       retries the whole transaction, which then sees the new stock.</li>
     * </ul>
     */
    public OrderDetail createOrder(CreateOrderRequest request) {
        return transactions.inTransaction(() -> createOrderInTransaction(request));
    }

    private OrderDetail createOrderInTransaction(CreateOrderRequest request) {
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        List<OrderDocument.Item> items = new ArrayList<>();
        Instant now = Instant.now();
        for (CreateOrderItemRequest itemRequest : request.items()) {
            SkuForOrder sku = repository.findSkuForOrder(itemRequest.skuId());
            support.require(sku != null, ErrorCode.SKU_UNAVAILABLE,
                    "Sản phẩm trong giỏ hàng không còn được bán (SKU " + itemRequest.skuId() + ").");
            support.require(sku.stock() >= itemRequest.quantity(), ErrorCode.OUT_OF_STOCK,
                    notEnoughStock(sku.productName(), sku.variantName()));
            BigDecimal unitPrice = MongoValues.money(Optional.ofNullable(sku.salePrice()).orElse(sku.price()));
            BigDecimal lineTotal = MongoValues.money(unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity())));
            subtotal = subtotal.add(lineTotal);
            items.add(new OrderDocument.Item(UUID.randomUUID(), sku.id(), sku.productName(), sku.skuCode(), sku.variantName(),
                    sku.size(), unitPrice, itemRequest.quantity(), lineTotal, now.plusMillis(items.size())));
        }

        Coupon coupon = null;
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            coupon = couponService.requireValidCoupon(request.couponCode(), subtotal);
            discount = MongoValues.money(couponService.calculateDiscount(coupon, subtotal));
        }

        BigDecimal shipping = MongoValues.money(Optional.ofNullable(request.shippingFee()).orElse(BigDecimal.ZERO));
        BigDecimal total = MongoValues.money(subtotal.subtract(discount).add(shipping).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        UUID orderId = UUID.randomUUID();
        String orderCode = "SHOE-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        for (OrderDocument.Item item : items) {
            int updated = repository.decrementStock(item.skuId(), item.quantity());
            support.require(updated == 1, ErrorCode.OUT_OF_STOCK, notEnoughStock(item.productName(), item.variantName()));
        }
        if (coupon != null) couponService.incrementCouponUsed(coupon.id());

        List<OrderDocument.History> history = List.of(new OrderDocument.History(UUID.randomUUID(), null, "PENDING",
                "Order created", support.adminId(), now));
        repository.insertOrder(new OrderDocument(orderId, orderCode, request.customerName(), request.customerPhone(),
                request.customerEmail(), request.shippingAddress(), request.note(), "PENDING",
                coupon == null ? null : coupon.id(), request.customerId(), request.paymentMethod(), "UNPAID", null,
                request.shippingMethod(), "PENDING", null, null, subtotal, discount, shipping, total,
                items, history, now, now, false, null));
        return getOrder(orderId);
    }

    public PageResult<Order> listOrders(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeOrderStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<Order> items = repository.listOrders(normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, repository.countOrders(normalizedStatus, keyword));
    }

    public OrderDetail getOrder(UUID id) {
        OrderDocument order = repository.findOrderDocument(id);
        support.requireFound(order != null, ErrorCode.ORDER_NOT_FOUND, "Order not found");
        return new OrderDetail(OrderRepository.toOrder(order), OrderRepository.items(order));
    }

    @Transactional
    public void updateOrderStatus(UUID id, String status, String note) {
        Order order = repository.findOrderById(id);
        support.requireFound(order != null, ErrorCode.ORDER_NOT_FOUND, "Order not found");
        String normalized = support.normalizeOrderStatusRequired(status);
        repository.updateOrderStatus(id, order.status(), normalized, note, support.adminId());
    }

    @Transactional
    public void updateOrderStatus(UUID id, String status) {
        updateOrderStatus(id, status, null);
    }

    @Transactional
    public void updatePayment(UUID id, PaymentUpdateRequest request) {
        support.requireFound(repository.findOrderById(id) != null, ErrorCode.ORDER_NOT_FOUND, "Order not found");
        repository.updatePayment(id, request.paymentMethod(), normalizePaymentStatus(request.paymentStatus()), request.paidAt());
    }

    @Transactional
    public void updateShipping(UUID id, ShippingUpdateRequest request) {
        support.requireFound(repository.findOrderById(id) != null, ErrorCode.ORDER_NOT_FOUND, "Order not found");
        repository.updateShipping(id, request.shippingMethod(), normalizeShippingStatus(request.shippingStatus()), request.trackingCode());
    }

    public List<OrderStatusHistory> listStatusHistory(UUID orderId) {
        OrderDocument order = repository.findOrderDocument(orderId);
        support.requireFound(order != null, ErrorCode.ORDER_NOT_FOUND, "Order not found");
        return OrderRepository.history(order);
    }

    /** Shown to the customer at checkout, so it names the product rather than the SKU code. */
    private static String notEnoughStock(String productName, String variantName) {
        String label = variantName == null || variantName.isBlank() ? productName : productName + " - " + variantName;
        return "Sản phẩm \"" + label + "\" không đủ số lượng trong kho.";
    }

    private String normalizePaymentStatus(String status) {
        if (status == null || status.isBlank()) return "UNPAID";
        String normalized = status.trim().toUpperCase();
        support.require(java.util.Set.of("UNPAID", "PAID", "REFUNDED", "FAILED").contains(normalized), ErrorCode.VALIDATION_ERROR, "Payment status is invalid");
        return normalized;
    }

    private String normalizeShippingStatus(String status) {
        if (status == null || status.isBlank()) return "PENDING";
        String normalized = status.trim().toUpperCase();
        support.require(java.util.Set.of("PENDING", "PACKING", "SHIPPING", "DELIVERED", "RETURNED", "CANCELLED").contains(normalized), ErrorCode.VALIDATION_ERROR, "Shipping status is invalid");
        return normalized;
    }
}
