package com.fss.backend.catalog.sku;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Sku(UUID id, UUID productId, UUID variantId, String skuCode, String size, BigDecimal price,
                  BigDecimal salePrice, Integer stock, String status, OffsetDateTime createdAt) {}
