package com.fss.backend.catalog.sku;

import java.math.BigDecimal;
import java.util.UUID;

public record SkuForOrder(UUID id, UUID productId, UUID variantId, String skuCode, String size, BigDecimal price,
                          BigDecimal salePrice, Integer stock, String status, String productName, String variantName) {}
