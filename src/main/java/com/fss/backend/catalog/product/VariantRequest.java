package com.fss.backend.catalog.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VariantRequest(UUID id, UUID clientId, @NotBlank String name, @NotNull UUID colorId, String colorName, String colorCode,
                             UUID imageFileId, String status, Integer sortOrder) {}
