package com.fss.backend.customer;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Customer(UUID id, String fullName, String email, String phone, String status, OffsetDateTime createdAt) {}
