package com.fss.backend.catalog.sku;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Collection {@code skus}. Money is Decimal128 with scale 2. {@code effectivePrice} = salePrice ?: price, kept on
 * write so price filters and "min sale price" can use an index. Orders decrement {@code stock} with a conditional
 * update ({@code stock >= quantity}) inside a transaction, so stock can never go negative.
 */
@Document(SkuDocument.COLLECTION)
public record SkuDocument(@Id UUID id, UUID productId, UUID variantId, String skuCode, UUID sizeId, BigDecimal price,
                          BigDecimal salePrice, BigDecimal effectivePrice, Integer stock, String status,
                          Instant createdAt, Instant updatedAt, boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "skus";
}
