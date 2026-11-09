package com.fss.backend.content.feedback;

import com.fss.backend.common.ErrorCode;
import com.fss.backend.common.PageResult;
import com.fss.backend.file.FileReferenceService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CustomerFeedbackService {
    private final CustomerFeedbackRepository repository;
    private final EcommerceSupport support;
    private final FileReferenceService fileReferenceService;

    public CustomerFeedbackService(CustomerFeedbackRepository repository, EcommerceSupport support,
                                   FileReferenceService fileReferenceService) {
        this.repository = repository;
        this.support = support;
        this.fileReferenceService = fileReferenceService;
    }

    public PageResult<CustomerFeedback> listPublic(int page, int limit) {
        int safeLimit = support.safeLimit(limit);
        List<CustomerFeedback> items = repository.list(true, null, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withImageUrl).toList();
        return support.pageResult(items, page, safeLimit, repository.count(true, null));
    }

    public PageResult<CustomerFeedback> listAdmin(String status, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<CustomerFeedback> items = repository.list(false, normalizedStatus, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withImageUrl).toList();
        return support.pageResult(items, page, safeLimit, repository.count(false, normalizedStatus));
    }

    public CustomerFeedback getAdmin(UUID id) {
        return withImageUrl(require(id));
    }

    @Transactional
    public UUID create(CustomerFeedbackRequest request) {
        UUID id = UUID.randomUUID();
        support.activateFile(request.fileId());
        repository.insert(id, request.fileId(), support.trimToNull(request.customerName()),
                support.trimToNull(request.description()), support.nz(request.sortOrder()),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.adminId());
        return id;
    }

    @Transactional
    public void update(UUID id, CustomerFeedbackRequest request) {
        CustomerFeedback existing = require(id);
        support.activateFile(request.fileId());
        repository.update(id, request.fileId(), support.trimToNull(request.customerName()),
                support.trimToNull(request.description()), support.nz(request.sortOrder()),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.adminId());
        if (!Objects.equals(existing.fileId(), request.fileId())) fileReferenceService.releaseFile(existing.fileId());
    }

    @Transactional
    public void delete(UUID id) {
        CustomerFeedback existing = require(id);
        repository.softDelete(id, support.adminId());
        fileReferenceService.releaseFile(existing.fileId());
    }

    private CustomerFeedback require(UUID id) {
        CustomerFeedback feedback = repository.findById(id);
        support.requireFound(feedback != null, ErrorCode.CUSTOMER_FEEDBACK_NOT_FOUND, "Customer feedback not found");
        return feedback;
    }

    private CustomerFeedback withImageUrl(CustomerFeedback feedback) {
        return new CustomerFeedback(feedback.id(), feedback.fileId(),
                feedback.imageUrl() == null ? null : support.publicUrl(feedback.imageUrl()), feedback.customerName(),
                feedback.description(), feedback.sortOrder(), feedback.status(), feedback.createdAt(),
                feedback.updatedAt());
    }
}
