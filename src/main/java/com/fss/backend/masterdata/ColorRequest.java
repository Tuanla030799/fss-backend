package com.fss.backend.masterdata;

import jakarta.validation.constraints.NotBlank;

public record ColorRequest(@NotBlank String name, String colorCode, String status, Integer sortOrder) {}
