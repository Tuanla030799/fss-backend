package com.fss.backend.catalog.category;

import com.fss.backend.common.ErrorCode;
import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {
    private final CategoryRepository repository;
    private final EcommerceSupport support;

    public CategoryService(CategoryRepository repository, EcommerceSupport support) {
        this.repository = repository;
        this.support = support;
    }

    public PageResult<Category> listPublicCategories(int page, int limit) {
        int safeLimit = support.safeLimit(limit);
        List<Category> items = repository.listCategories(EcommerceSupport.ACTIVE, null, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, repository.countCategories(EcommerceSupport.ACTIVE, null));
    }

    public PageResult<Category> listAdminCategories(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<Category> items = repository.listCategories(normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, repository.countCategories(normalizedStatus, keyword));
    }

    @Transactional
    public UUID createCategory(CategoryRequest request) {
        UUID id = UUID.randomUUID();
        String slug = uniqueSlug(request.slug(), request.name(), null);
        repository.insertCategory(id, request.parentId(), request.name().trim(), slug, request.description(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.nz(request.sortOrder()), support.adminId());
        return id;
    }

    @Transactional
    public void updateCategory(UUID id, CategoryRequest request) {
        support.requireFound(repository.findCategoryById(id) != null, ErrorCode.CATEGORY_NOT_FOUND, "Category not found");
        String slug = uniqueSlug(request.slug(), request.name(), id);
        repository.updateCategory(id, request.parentId(), request.name().trim(), slug, request.description(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.nz(request.sortOrder()), support.adminId());
    }

    @Transactional
    public void deleteCategory(UUID id) {
        support.requireFound(repository.findCategoryById(id) != null, ErrorCode.CATEGORY_NOT_FOUND, "Category not found");
        repository.softDeleteCategory(id, support.adminId());
    }

    private String uniqueSlug(String slug, String name, UUID exclude) {
        String normalized = support.uniqueSlug(slug, name);
        support.requireUnique(!repository.slugExists(normalized, exclude), ErrorCode.SLUG_ALREADY_EXISTS, "Category slug already exists");
        return normalized;
    }
}
