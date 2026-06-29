package com.fss.backend.auth.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface AdminAccountMapper {
    List<AdminAccount> listAdminAccounts(@Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);
    long countAdminAccounts(@Param("keyword") String keyword);
    AdminAccount findAdminAccountById(@Param("id") UUID id);
    int countAdminEmail(@Param("email") String email, @Param("excludeId") UUID excludeId);
    void insertAdminAccount(@Param("id") UUID id, @Param("name") String name, @Param("email") String email,
                            @Param("passwordHash") String passwordHash, @Param("role") String role, @Param("status") String status);
    void updateAdminAccount(@Param("id") UUID id, @Param("name") String name, @Param("email") String email,
                            @Param("passwordHash") String passwordHash, @Param("role") String role, @Param("status") String status);
    void softDeleteAdminAccount(@Param("id") UUID id);
}
