package com.fss.backend.customer;

import com.fss.backend.common.ApiResponse;
import com.fss.backend.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/admin/customers")
    public ApiResponse<PageResult<Customer>> listCustomers(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "1") @Min(1) int page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok("OK", service.listCustomers(status, keyword, page, limit));
    }

    @GetMapping("/admin/customers/{id}")
    public ApiResponse<Customer> getCustomer(@PathVariable UUID id) {
        return ApiResponse.ok("OK", service.getCustomer(id));
    }

    @PostMapping("/admin/customers")
    public ApiResponse<Map<String, UUID>> createCustomer(@Valid @RequestBody CustomerRequest body) {
        return ApiResponse.ok("Created", Map.of("id", service.createCustomer(body)));
    }

    @PutMapping("/admin/customers/{id}")
    public ApiResponse<Void> updateCustomer(@PathVariable UUID id, @Valid @RequestBody CustomerRequest body) {
        service.updateCustomer(id, body);
        return ApiResponse.ok("Updated", null);
    }

    @DeleteMapping("/admin/customers/{id}")
    public ApiResponse<Void> deleteCustomer(@PathVariable UUID id) {
        service.deleteCustomer(id);
        return ApiResponse.ok("Deleted", null);
    }
}
