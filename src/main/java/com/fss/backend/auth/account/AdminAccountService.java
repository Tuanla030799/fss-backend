package com.fss.backend.auth.account;

import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminAccountService {
    private final AdminAccountMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final EcommerceSupport support;

    public AdminAccountService(AdminAccountMapper mapper, PasswordEncoder passwordEncoder, EcommerceSupport support) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.support = support;
    }

    public PageResult<AdminAccount> listAdminAccounts(String keyword, int page, int limit) {
        int safeLimit = support.safeLimit(limit);
        List<AdminAccount> items = mapper.listAdminAccounts(keyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, mapper.countAdminAccounts(keyword));
    }

    @Transactional
    public UUID createAdminAccount(AdminAccountRequest request) {
        support.require(request.password() != null && !request.password().isBlank(), "Password is required");
        support.require(mapper.countAdminEmail(request.email(), null) == 0, "Email already exists");
        UUID id = UUID.randomUUID();
        mapper.insertAdminAccount(id, request.name(), request.email(), passwordEncoder.encode(request.password()), role(request.role()),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE));
        return id;
    }

    @Transactional
    public void updateAdminAccount(UUID id, AdminAccountRequest request) {
        support.require(mapper.findAdminAccountById(id) != null, "Admin user not found");
        support.require(mapper.countAdminEmail(request.email(), id) == 0, "Email already exists");
        mapper.updateAdminAccount(id, request.name(), request.email(),
                request.password() == null || request.password().isBlank() ? null : passwordEncoder.encode(request.password()),
                role(request.role()), support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE));
    }

    @Transactional
    public void deleteAdminAccount(UUID id) {
        support.require(mapper.findAdminAccountById(id) != null, "Admin user not found");
        mapper.softDeleteAdminAccount(id);
    }

    private String role(String value) {
        return value == null || value.isBlank() ? "operator" : value.trim();
    }
}
