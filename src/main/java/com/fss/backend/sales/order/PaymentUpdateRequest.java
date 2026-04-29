package com.fss.backend.sales.order;

import java.time.OffsetDateTime;

public record PaymentUpdateRequest(String paymentMethod, String paymentStatus, OffsetDateTime paidAt) {}
