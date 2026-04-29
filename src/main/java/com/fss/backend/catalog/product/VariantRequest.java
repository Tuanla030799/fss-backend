package com.fss.backend.catalog.product;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record VariantRequest(UUID id, UUID clientId, @NotBlank String name, String colorName, String colorCode,
                             UUID imageFileId, String status, Integer sortOrder) {}
