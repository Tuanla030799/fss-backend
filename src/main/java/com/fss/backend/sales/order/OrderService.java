package com.fss.backend.sales.order;

import com.fss.backend.promotion.coupon.Coupon;
import com.fss.backend.promotion.coupon.CouponService;
import com.fss.backend.catalog.sku.SkuForOrder;
import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderMapper mapper;
    private final CouponService couponService;
    private final EcommerceSupport support;

    public OrderService(OrderMapper mapper, CouponService couponService, EcommerceSupport support) {
        this.mapper = mapper;
        this.couponService = couponService;
        this.support = support;
    }

    @Transactional
    public OrderDetail createOrder(CreateOrderRequest request) {
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (CreateOrderItemRequest itemRequest : request.items()) {
            SkuForOrder sku = mapper.findSkuForOrder(itemRequest.skuId());
            support.require(sku != null, "SKU not found or inactive: " + itemRequest.skuId());
            support.require(sku.stock() >= itemRequest.quantity(), "Not enough stock for SKU: " + sku.skuCode());
            BigDecimal unitPrice = Optional.ofNullable(sku.salePrice()).orElse(sku.price());
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity()));
            subtotal = subtotal.add(lineTotal);
            items.add(new OrderItem(UUID.randomUUID(), null, sku.id(), sku.productName(), sku.skuCode(), sku.variantName(),
                    sku.size(), unitPrice, itemRequest.quantity(), lineTotal));
        }

        Coupon coupon = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            coupon = couponService.requireValidCoupon(request.couponCode(), subtotal);
            discount = couponService.calculateDiscount(coupon, subtotal);
        }

        BigDecimal shipping = Optional.ofNullable(request.shippingFee()).orElse(BigDecimal.ZERO);
        BigDecimal total = subtotal.subtract(discount).add(shipping).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        UUID orderId = UUID.randomUUID();
        String orderCode = "SHOE-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        mapper.insertOrder(orderId, orderCode, request.customerName(), request.customerPhone(), request.customerEmail(),
                request.shippingAddress(), request.note(), "PENDING", coupon == null ? null : coupon.id(), request.customerId(), request.paymentMethod(), request.shippingMethod(), subtotal, discount, shipping, total);
        mapper.insertStatusHistory(UUID.randomUUID(), orderId, null, "PENDING", "Order created", support.adminId());
        for (OrderItem item : items) {
            int updated = mapper.decrementStock(item.skuId(), item.quantity());
            support.require(updated == 1, "Not enough stock for SKU: " + item.skuCode());
            mapper.insertOrderItem(item.id(), orderId, item.skuId(), item.productName(), item.skuCode(), item.variantName(),
                    item.size(), item.unitPrice(), item.quantity(), item.lineTotal());
        }
        if (coupon != null) couponService.incrementCouponUsed(coupon.id());
        return getOrder(orderId);
    }

    public PageResult<Order> listOrders(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeOrderStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<Order> items = mapper.listOrders(normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, mapper.countOrders(normalizedStatus, keyword));
    }

    public OrderDetail getOrder(UUID id) {
        Order order = mapper.findOrderById(id);
        support.require(order != null, "Order not found");
        return new OrderDetail(order, mapper.listOrderItems(id));
    }

    @Transactional
    public void updateOrderStatus(UUID id, String status, String note) {
        Order order = mapper.findOrderById(id);
        support.require(order != null, "Order not found");
        String normalized = support.normalizeOrderStatusRequired(status);
        mapper.updateOrderStatus(id, normalized);
        mapper.insertStatusHistory(UUID.randomUUID(), id, order.status(), normalized, note, support.adminId());
    }

    @Transactional
    public void updateOrderStatus(UUID id, String status) {
        updateOrderStatus(id, status, null);
    }

    @Transactional
    public void updatePayment(UUID id, PaymentUpdateRequest request) {
        support.require(mapper.findOrderById(id) != null, "Order not found");
        mapper.updatePayment(id, request.paymentMethod(), normalizePaymentStatus(request.paymentStatus()), request.paidAt());
    }

    @Transactional
    public void updateShipping(UUID id, ShippingUpdateRequest request) {
        support.require(mapper.findOrderById(id) != null, "Order not found");
        mapper.updateShipping(id, request.shippingMethod(), normalizeShippingStatus(request.shippingStatus()), request.trackingCode());
    }

    public List<OrderStatusHistory> listStatusHistory(UUID orderId) {
        support.require(mapper.findOrderById(orderId) != null, "Order not found");
        return mapper.listStatusHistory(orderId);
    }

    private String normalizePaymentStatus(String status) {
        if (status == null || status.isBlank()) return "UNPAID";
        String normalized = status.trim().toUpperCase();
        support.require(java.util.Set.of("UNPAID", "PAID", "REFUNDED", "FAILED").contains(normalized), "Payment status is invalid");
        return normalized;
    }

    private String normalizeShippingStatus(String status) {
        if (status == null || status.isBlank()) return "PENDING";
        String normalized = status.trim().toUpperCase();
        support.require(java.util.Set.of("PENDING", "PACKING", "SHIPPING", "DELIVERED", "RETURNED", "CANCELLED").contains(normalized), "Shipping status is invalid");
        return normalized;
    }
}
