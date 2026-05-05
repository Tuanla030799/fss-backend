package com.fss.backend.masterdata;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ColorOption(UUID id, String value, String label, String colorCode,
                          String status, Integer sortOrder, OffsetDateTime createdAt) {}
