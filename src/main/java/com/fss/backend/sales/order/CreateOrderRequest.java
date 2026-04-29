package com.fss.backend.sales.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(UUID customerId, @NotBlank String customerName, @NotBlank String customerPhone, String customerEmail,
                                 @NotBlank String shippingAddress, String note, String couponCode,
                                 String paymentMethod, String shippingMethod,
                                 @DecimalMin("0.00") BigDecimal shippingFee,
                                 @NotEmpty List<@Valid CreateOrderItemRequest> items) {}
