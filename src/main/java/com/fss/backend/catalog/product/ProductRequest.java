package com.fss.backend.catalog.product;

import com.fss.backend.catalog.sku.SkuRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ProductRequest(@NotNull UUID categoryId, UUID brandId, String gender, @NotBlank String name, String slug, String shortDescription,
                             String descriptionJson, String status, Boolean isFeatured, Integer featuredOrder,
                             List<@Valid ProductImageRequest> images,
                             List<@Valid VariantRequest> variants,
                             List<@Valid SkuRequest> skus) {}
