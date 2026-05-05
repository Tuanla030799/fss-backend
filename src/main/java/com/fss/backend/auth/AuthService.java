package com.fss.backend.auth;

import com.fss.backend.common.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(AuthMapper authMapper, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    public void register(AuthDtos.RegisterRequest request) {
        if (authMapper.findAdminByEmail(request.email()) != null) {
            throw new ApiException("Email already exists");
        }
        authMapper.insertAdmin(UUID.randomUUID(), request.name(), request.email(),
                passwordEncoder.encode(request.password()), request.role());
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        var admin = authMapper.findAdminByEmail(request.email());
        if (admin == null || !passwordEncoder.matches(request.password(), admin.passwordHash())) {
            throw new ApiException("Invalid credentials");
        }
        var access = jwtProvider.newAccessToken(admin.id(), admin.role());
        var refresh = jwtProvider.newRefreshToken(admin.id());
        authMapper.insertRefreshToken(UUID.randomUUID(), admin.id(), refresh, OffsetDateTime.now().plusDays(30));
        return new AuthDtos.TokenResponse(access, refresh);
    }

    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        String adminId = authMapper.findAdminIdByRefreshToken(request.refreshToken());
        if (adminId == null) {
            throw new ApiException("Refresh token invalid or expired");
        }
        var admin = authMapper.findAdminById(UUID.fromString(adminId));
        var access = jwtProvider.newAccessToken(admin.id(), admin.role());
        var refresh = jwtProvider.newRefreshToken(admin.id());
        authMapper.deleteRefreshToken(request.refreshToken());
        authMapper.insertRefreshToken(UUID.randomUUID(), admin.id(), refresh, OffsetDateTime.now().plusDays(30));
        return new AuthDtos.TokenResponse(access, refresh);
    }

    public void logout(String refreshToken) {
        authMapper.deleteRefreshToken(refreshToken);
    }
}
