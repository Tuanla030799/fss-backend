package com.fss.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Refresh token cookie for the admin app. HttpOnly keeps it away from scripts (an XSS cannot read it) and the path
 * limits it to the auth endpoints, so it is never sent with any other request.
 */
@Component
public class RefreshTokenCookie {
    public static final String NAME = "fss_refresh_token";
    static final String PATH = "/api/admin/auth";

    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    public RefreshTokenCookie(@Value("${app.auth.refresh-cookie.secure:true}") boolean secure,
                              @Value("${app.auth.refresh-cookie.same-site:Strict}") String sameSite,
                              JwtProvider jwtProvider) {
        this.sameSite = switch (sameSite.trim().toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            case "none" -> "None";
            default -> throw new IllegalStateException("app.auth.refresh-cookie.same-site must be Strict, Lax or None");
        };
        if ("None".equals(this.sameSite) && !secure) {
            // Browsers drop SameSite=None cookies that are not Secure.
            throw new IllegalStateException("app.auth.refresh-cookie.same-site=None requires app.auth.refresh-cookie.secure=true");
        }
        this.secure = secure;
        this.maxAge = jwtProvider.refreshTokenTtl();
    }

    public ResponseCookie issue(String refreshToken) {
        return build(refreshToken, maxAge);
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite(sameSite).path(PATH)
                .maxAge(maxAge).build();
    }
}
