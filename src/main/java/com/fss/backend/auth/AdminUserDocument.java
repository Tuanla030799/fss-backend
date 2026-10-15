package com.fss.backend.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Collection {@code admin_users}. {@code emailKey} is the lower-cased email used for uniqueness and login. */
@Document(AdminUserDocument.COLLECTION)
public record AdminUserDocument(@Id UUID id, String name, String email, String emailKey, String passwordHash,
                                String role, String status, Instant createdAt, Instant updatedAt,
                                boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "admin_users";
}
