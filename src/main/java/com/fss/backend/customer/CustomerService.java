package com.fss.backend.customer;

import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerMapper mapper;
    private final EcommerceSupport support;

    public CustomerService(CustomerMapper mapper, EcommerceSupport support) {
        this.mapper = mapper;
        this.support = support;
    }

    public PageResult<Customer> listCustomers(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<Customer> items = mapper.listCustomers(normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, mapper.countCustomers(normalizedStatus, keyword));
    }

    public Customer getCustomer(UUID id) {
        Customer customer = mapper.findCustomerById(id);
        support.require(customer != null, "Customer not found");
        return customer;
    }

    @Transactional
    public UUID createCustomer(CustomerRequest request) {
        validateUnique(request.email(), request.phone(), null);
        UUID id = UUID.randomUUID();
        mapper.insertCustomer(id, request.fullName(), normalizeBlank(request.email()), request.phone(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE));
        return id;
    }

    @Transactional
    public void updateCustomer(UUID id, CustomerRequest request) {
        support.require(mapper.findCustomerById(id) != null, "Customer not found");
        validateUnique(request.email(), request.phone(), id);
        mapper.updateCustomer(id, request.fullName(), normalizeBlank(request.email()), request.phone(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE));
    }

    @Transactional
    public void deleteCustomer(UUID id) {
        support.require(mapper.findCustomerById(id) != null, "Customer not found");
        mapper.softDeleteCustomer(id);
    }

    private void validateUnique(String email, String phone, UUID excludeId) {
        if (email != null && !email.isBlank()) {
            support.require(mapper.countEmail(email, excludeId) == 0, "Email already exists");
        }
        support.require(mapper.countPhone(phone, excludeId) == 0, "Phone already exists");
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
