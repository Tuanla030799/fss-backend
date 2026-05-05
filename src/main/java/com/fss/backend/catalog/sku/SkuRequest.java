package com.fss.backend.catalog.sku;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SkuRequest(UUID id, UUID variantClientId, UUID variantId, @NotBlank String skuCode, @NotNull UUID sizeId, String size,
                         @NotNull @DecimalMin("0.00") BigDecimal price,
                         @DecimalMin("0.00") BigDecimal salePrice,
                         @NotNull @Min(0) Integer stock, String status) {}
