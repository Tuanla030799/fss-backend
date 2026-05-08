package com.fss.backend.content.collection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record ProductCollectionRequest(@NotBlank String name, String slug, String description, String descriptionHtml,
                                       UUID fileId, String status, Integer sortOrder,
                                       List<@Valid CollectionProductRequest> products) {}
