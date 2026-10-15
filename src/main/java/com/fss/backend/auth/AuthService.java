package com.fss.backend.auth;

import com.fss.backend.common.ApiException;
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
            throw new ApiException("Public admin registration is disabled");
        }
        if (authRepository.adminEmailExists(request.email())) {
            throw new ApiException("Email already exists");
        }
        authRepository.insertAdmin(UUID.randomUUID(), request.name(), request.email(),
                passwordEncoder.encode(request.password()), request.role());
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        var admin = authRepository.findAdminByEmail(request.email());
        if (admin == null || !passwordEncoder.matches(request.password(), admin.passwordHash())) {
            throw new ApiException("Invalid credentials");
        }
        var access = jwtProvider.newAccessToken(admin.id(), admin.role());
        var refresh = jwtProvider.newRefreshToken(admin.id());
        authRepository.insertRefreshToken(UUID.randomUUID(), admin.id(), refresh, OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));
        return new AuthDtos.TokenResponse(access, refresh);
    }

    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        UUID adminId = authRepository.findAdminIdByRefreshToken(request.refreshToken());
        if (adminId == null || !authRepository.revokeRefreshToken(request.refreshToken())) {
            throw new ApiException("Refresh token invalid or expired");
        }
        var admin = authRepository.findAdminById(adminId);
        if (admin == null) {
            throw new ApiException("Refresh token invalid or expired");
        }
        var access = jwtProvider.newAccessToken(admin.id(), admin.role());
        var refresh = jwtProvider.newRefreshToken(admin.id());
        authRepository.insertRefreshToken(UUID.randomUUID(), admin.id(), refresh, OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));
        return new AuthDtos.TokenResponse(access, refresh);
    }

    public void logout(String refreshToken) {
        authRepository.revokeRefreshToken(refreshToken);
    }
}
