package com.fss.backend.customer;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Collection {@code customers}. {@code emailKey} (lower-cased, absent when no email) backs the unique index. */
@Document(CustomerDocument.COLLECTION)
public record CustomerDocument(@Id UUID id, String fullName, String email, String emailKey, String phone, String passwordHash,
                               String status, Instant createdAt, Instant updatedAt, boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "customers";
}
