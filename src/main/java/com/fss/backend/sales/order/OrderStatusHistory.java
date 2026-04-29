package com.fss.backend.sales.order;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderStatusHistory(UUID id, UUID orderId, String oldStatus, String newStatus, String note,
                                 UUID createdBy, OffsetDateTime createdAt) {}
