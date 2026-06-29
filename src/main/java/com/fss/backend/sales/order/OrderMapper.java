package com.fss.backend.sales.order;

import com.fss.backend.catalog.sku.SkuForOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OrderMapper {
    SkuForOrder findSkuForOrder(@Param("id") UUID id);
    int decrementStock(@Param("skuId") UUID skuId, @Param("quantity") int quantity);
    void insertOrder(@Param("id") UUID id, @Param("orderCode") String orderCode, @Param("customerName") String customerName,
                     @Param("customerPhone") String customerPhone, @Param("customerEmail") String customerEmail,
                     @Param("shippingAddress") String shippingAddress, @Param("note") String note,
                     @Param("status") String status, @Param("couponId") UUID couponId, @Param("customerId") UUID customerId,
                     @Param("paymentMethod") String paymentMethod, @Param("shippingMethod") String shippingMethod,
                     @Param("subtotalAmount") BigDecimal subtotalAmount, @Param("discountAmount") BigDecimal discountAmount,
                     @Param("shippingFee") BigDecimal shippingFee, @Param("totalAmount") BigDecimal totalAmount);
    void insertOrderItem(@Param("id") UUID id, @Param("orderId") UUID orderId, @Param("skuId") UUID skuId,
                         @Param("productName") String productName, @Param("skuCode") String skuCode,
                         @Param("variantName") String variantName, @Param("size") String size,
                         @Param("unitPrice") BigDecimal unitPrice, @Param("quantity") Integer quantity,
                         @Param("lineTotal") BigDecimal lineTotal);
    List<Order> listOrders(@Param("status") String status, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);
    long countOrders(@Param("status") String status, @Param("keyword") String keyword);
    Order findOrderById(@Param("id") UUID id);
    List<OrderItem> listOrderItems(@Param("orderId") UUID orderId);
    void updateOrderStatus(@Param("id") UUID id, @Param("status") String status);
    void updatePayment(@Param("id") UUID id, @Param("paymentMethod") String paymentMethod, @Param("paymentStatus") String paymentStatus, @Param("paidAt") OffsetDateTime paidAt);
    void updateShipping(@Param("id") UUID id, @Param("shippingMethod") String shippingMethod, @Param("shippingStatus") String shippingStatus, @Param("trackingCode") String trackingCode);
    void insertStatusHistory(@Param("id") UUID id, @Param("orderId") UUID orderId, @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus, @Param("note") String note, @Param("createdBy") UUID createdBy);
    List<OrderStatusHistory> listStatusHistory(@Param("orderId") UUID orderId);
}
