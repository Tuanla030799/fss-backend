package com.fss.backend.content.collection;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CollectionProductRequest(@NotNull UUID productId, Integer sortOrder) {}
