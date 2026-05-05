package com.fss.backend.masterdata;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SizeOption(UUID id, String value, String label, String status,
                         Integer sortOrder, OffsetDateTime createdAt) {}
