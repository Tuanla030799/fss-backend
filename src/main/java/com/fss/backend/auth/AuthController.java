package com.fss.backend.auth;

import com.fss.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    public AuthController(AuthService authService, RefreshTokenCookie refreshTokenCookie) {
        this.authService = authService;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        authService.register(request);
        return ApiResponse.ok("Registered", null);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDtos.TokenResponse>> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return withRefreshCookie("Logged in", authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthDtos.TokenResponse>> refresh(
            @RequestBody(required = false) AuthDtos.RefreshRequest request,
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String cookieToken) {
        return withRefreshCookie("Refreshed", authService.refresh(refreshToken(request, cookieToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) AuthDtos.RefreshRequest request,
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String cookieToken) {
        authService.logout(refreshToken(request, cookieToken));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString())
                .body(ApiResponse.ok("Logged out", null));
    }

    /** The body still carries the refresh token for clients that keep it themselves; the cookie is the safer option. */
    private ResponseEntity<ApiResponse<AuthDtos.TokenResponse>> withRefreshCookie(String message, AuthDtos.TokenResponse tokens) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshTokenCookie.issue(tokens.refreshToken()).toString())
                .body(ApiResponse.ok(message, tokens));
    }

    /** A token in the body wins (existing clients); otherwise the HttpOnly cookie is used. */
    private static String refreshToken(AuthDtos.RefreshRequest request, String cookieToken) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        return cookieToken;
    }
}
