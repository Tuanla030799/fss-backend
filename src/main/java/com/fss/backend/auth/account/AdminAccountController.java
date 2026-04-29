package com.fss.backend.auth.account;

import com.fss.backend.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AdminAccountController {
    private final AdminAccountService service;

    public AdminAccountController(AdminAccountService service) {
        this.service = service;
    }

    @GetMapping("/admin/users")
    public ApiResponse<List<AdminAccount>> adminUsers(@RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") @Min(1) int page,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listAdminAccounts(keyword, page, limit));
    }

    @PostMapping("/admin/users")
    public ApiResponse<Map<String, UUID>> createAdminUser(@Valid @RequestBody AdminAccountRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createAdminAccount(body)));
    }

    @PutMapping("/admin/users/{id}")
    public ApiResponse<Void> updateAdminUser(@PathVariable UUID id, @Valid @RequestBody AdminAccountRequest body) {
        service.updateAdminAccount(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/users/{id}")
    public ApiResponse<Void> deleteAdminUser(@PathVariable UUID id) {
        service.deleteAdminAccount(id);
        return ApiResponse.ok("Deleted", null);
    }
}
