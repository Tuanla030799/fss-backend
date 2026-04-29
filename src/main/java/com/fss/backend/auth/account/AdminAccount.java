package com.fss.backend.auth.account;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAccount(UUID id, String name, String email, String role, String status, OffsetDateTime createdAt) {}
