package com.fss.backend.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

@Mapper
public interface AuthMapper {
    AdminUser findAdminByEmail(@Param("email") String email);
    AdminUser findAdminById(@Param("id") UUID id);
    void insertAdmin(@Param("id") UUID id, @Param("name") String name, @Param("email") String email,
                     @Param("passwordHash") String passwordHash, @Param("role") String role);

    void insertRefreshToken(@Param("id") UUID id, @Param("adminId") UUID adminId,
                            @Param("token") String token, @Param("expiresAt") OffsetDateTime expiresAt);
    UUID findAdminIdByRefreshToken(@Param("token") String token);
    void deleteRefreshToken(@Param("token") String token);
}
