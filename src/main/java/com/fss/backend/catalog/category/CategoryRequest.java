package com.fss.backend.catalog.category;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CategoryRequest(UUID parentId, @NotBlank String name, String slug, String description,
                              String status, Integer sortOrder) {}
