package com.fss.backend.sales.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItem(UUID id, UUID orderId, UUID skuId, String productName, String skuCode, String variantName,
                        String size, BigDecimal unitPrice, Integer quantity, BigDecimal lineTotal) {}
