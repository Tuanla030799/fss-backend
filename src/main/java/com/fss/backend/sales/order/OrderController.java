package com.fss.backend.sales.order;

import com.fss.backend.common.ApiResponse;
import com.fss.backend.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    public ApiResponse<OrderDetail> createOrder(@Valid @RequestBody CreateOrderRequest body) {
        return ApiResponse.ok("Created", service.createOrder(body));
    }

    @GetMapping("/admin/orders")
    public ApiResponse<PageResult<Order>> adminOrders(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") @Min(1) int page,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listOrders(status, keyword, page, limit));
    }

    @GetMapping("/admin/orders/{id}")
    public ApiResponse<OrderDetail> adminOrderDetail(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.getOrder(id));
    }

    @PatchMapping("/admin/orders/{id}/status")
    public ApiResponse<Void> updateOrderStatus(@PathVariable UUID id, @Valid @RequestBody OrderStatusRequest body) {
        service.updateOrderStatus(id, body.status(), body.note());
        return ApiResponse.ok("Updated", null);
    }

    @PatchMapping("/admin/orders/{id}/payment")
    public ApiResponse<Void> updatePayment(@PathVariable UUID id, @RequestBody PaymentUpdateRequest body) {
        service.updatePayment(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @PatchMapping("/admin/orders/{id}/shipping")
    public ApiResponse<Void> updateShipping(@PathVariable UUID id, @RequestBody ShippingUpdateRequest body) {
        service.updateShipping(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @GetMapping("/admin/orders/{id}/status-history")
    public ApiResponse<List<OrderStatusHistory>> statusHistory(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.listStatusHistory(id));
    }

}
