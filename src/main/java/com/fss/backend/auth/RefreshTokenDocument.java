package com.fss.backend.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Collection {@code refresh_tokens}. A TTL index on {@code expiresAt} removes expired tokens. */
@Document(RefreshTokenDocument.COLLECTION)
public record RefreshTokenDocument(@Id UUID id, UUID adminId, String token, Instant expiresAt, Instant revokedAt,
                                   Instant createdAt) {
    public static final String COLLECTION = "refresh_tokens";
}
