package com.fss.backend.customer;

import com.fss.backend.common.ErrorCode;
import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerRepository repository;
    private final EcommerceSupport support;

    public CustomerService(CustomerRepository repository, EcommerceSupport support) {
        this.repository = repository;
        this.support = support;
    }

    public PageResult<Customer> listCustomers(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<Customer> items = repository.listCustomers(normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, repository.countCustomers(normalizedStatus, keyword));
    }

    public Customer getCustomer(UUID id) {
        Customer customer = repository.findCustomerById(id);
        support.requireFound(customer != null, ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found");
        return customer;
    }

    @Transactional
    public UUID createCustomer(CustomerRequest request) {
        validateUnique(request.email(), request.phone(), null);
        UUID id = UUID.randomUUID();
        repository.insertCustomer(id, request.fullName(), normalizeBlank(request.email()), request.phone(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE));
        return id;
    }

    @Transactional
    public void updateCustomer(UUID id, CustomerRequest request) {
        support.requireFound(repository.findCustomerById(id) != null, ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found");
        validateUnique(request.email(), request.phone(), id);
        repository.updateCustomer(id, request.fullName(), normalizeBlank(request.email()), request.phone(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE));
    }

    @Transactional
    public void deleteCustomer(UUID id) {
        support.requireFound(repository.findCustomerById(id) != null, ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found");
        repository.softDeleteCustomer(id);
    }

    private void validateUnique(String email, String phone, UUID excludeId) {
        if (email != null && !email.isBlank()) {
            support.requireUnique(!repository.emailExists(email, excludeId), ErrorCode.EMAIL_ALREADY_EXISTS, "Email already exists");
        }
        support.requireUnique(!repository.phoneExists(phone, excludeId), ErrorCode.PHONE_ALREADY_EXISTS, "Phone already exists");
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
