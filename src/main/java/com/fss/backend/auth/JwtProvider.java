package com.fss.backend.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {
    private final SecretKey key;
    private final long accessMinutes;
    private final long refreshDays;

    static final String DEVELOPMENT_SECRET = "super-secret-change-me-super-secret-change-me";

    @Autowired
    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.access-token-minutes}") long accessMinutes,
                       @Value("${app.jwt.refresh-token-days}") long refreshDays,
                       @Value("${app.security.require-strong-jwt-secret:false}") boolean requireStrongSecret) {
        if (requireStrongSecret) {
            validateProductionSecret(secret);
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
    }

    public JwtProvider(String secret, long accessMinutes, long refreshDays) {
        this(secret, accessMinutes, refreshDays, false);
    }

    /** Production must not start with the development default or a short secret (HS256 needs >= 256 bits). */
    static void validateProductionSecret(String secret) {
        if (secret == null || secret.isBlank() || DEVELOPMENT_SECRET.equals(secret) || secret.contains("change-me")
                || secret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalStateException("APP_JWT_SECRET must be set to a random value of at least 64 characters");
        }
    }

    public String newAccessToken(UUID adminId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(adminId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public String newRefreshToken(UUID adminId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(adminId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshDays * 24 * 3600)))
                .signWith(key)
                .compact();
    }

    public UUID parseSubject(String token) {
        var subject = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
        return UUID.fromString(subject);
    }
}
