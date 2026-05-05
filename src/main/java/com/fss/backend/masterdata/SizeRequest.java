package com.fss.backend.masterdata;

import jakarta.validation.constraints.NotBlank;

public record SizeRequest(@NotBlank String value, String label, String status, Integer sortOrder) {}
