package com.fss.backend.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUser(UUID id, String name, String email, String passwordHash, String role, OffsetDateTime createdAt) {}
