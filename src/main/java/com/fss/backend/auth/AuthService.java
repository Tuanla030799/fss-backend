package com.fss.backend.auth;

import com.fss.backend.common.ApiException;
import com.fss.backend.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AuthService {
    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final boolean allowPublicAdminRegistration;

    public AuthService(AuthRepository authRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       @Value("${app.auth.allow-public-admin-registration:false}") boolean allowPublicAdminRegistration) {
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.allowPublicAdminRegistration = allowPublicAdminRegistration;
    }

    public void register(AuthDtos.RegisterRequest request) {
        if (!allowPublicAdminRegistration) {
            throw new ApiException(ErrorCode.AUTH_REGISTRATION_DISABLED, "Public admin registration is disabled");
        }
        if (authRepository.adminEmailExists(request.email())) {
            throw ApiException.conflict(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already exists");
        }
        authRepository.insertAdmin(UUID.randomUUID(), request.name(), request.email(),
                passwordEncoder.encode(request.password()), request.role());
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        var admin = authRepository.findAdminByEmail(request.email());
        if (admin == null || !passwordEncoder.matches(request.password(), admin.passwordHash())) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
        }
        return issueTokens(admin);
    }

    public AuthDtos.TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID, "Refresh token is missing");
        }
        UUID adminId = authRepository.findAdminIdByRefreshToken(refreshToken);
        if (adminId == null || !authRepository.revokeRefreshToken(refreshToken)) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID, "Refresh token invalid or expired");
        }
        var admin = authRepository.findAdminById(adminId);
        if (admin == null) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID, "Refresh token invalid or expired");
        }
        return issueTokens(admin);
    }

    /** Logging out without a token (already cleared on the client) is not an error. */
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authRepository.revokeRefreshToken(refreshToken);
        }
    }

    private AuthDtos.TokenResponse issueTokens(AdminUser admin) {
        var access = jwtProvider.newAccessToken(admin.id(), admin.role());
        var refresh = jwtProvider.newRefreshToken(admin.id());
        // Same lifetime as the refresh JWT and the refresh cookie (app.jwt.refresh-token-days).
        authRepository.insertRefreshToken(UUID.randomUUID(), admin.id(), refresh,
                OffsetDateTime.now(ZoneOffset.UTC).plus(jwtProvider.refreshTokenTtl()));
        return new AuthDtos.TokenResponse(access, refresh);
    }
}
