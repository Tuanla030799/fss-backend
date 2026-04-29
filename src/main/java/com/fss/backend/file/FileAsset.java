package com.fss.backend.file;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FileAsset(UUID id, String path, String status, OffsetDateTime createdAt) {}
