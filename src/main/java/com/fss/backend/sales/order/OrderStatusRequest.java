package com.fss.backend.sales.order;

import jakarta.validation.constraints.NotBlank;

public record OrderStatusRequest(@NotBlank String status, String note) {}
