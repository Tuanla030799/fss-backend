package com.fss.backend.catalog.brand;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record BrandRequest(@NotBlank String name, String slug, String description, UUID fileId,
                           String status, Integer sortOrder) {}
