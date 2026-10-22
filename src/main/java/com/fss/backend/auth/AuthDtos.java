package com.fss.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
    public record RegisterRequest(@NotBlank String name, @Email String email, @NotBlank String password,
                                  @NotBlank String role) {}
    public record LoginRequest(@Email String email, @NotBlank String password) {}
    /** The token may instead come from the HttpOnly refresh cookie, so it is optional here. */
    public record RefreshRequest(String refreshToken) {}
    public record TokenResponse(String accessToken, String refreshToken) {}
}
