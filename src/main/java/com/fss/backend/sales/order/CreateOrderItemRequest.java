package com.fss.backend.sales.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderItemRequest(@NotNull UUID skuId, @NotNull @Min(1) Integer quantity) {}
