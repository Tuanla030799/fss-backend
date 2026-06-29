package com.fss.backend.catalog.category;

import com.fss.backend.common.PageResult;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {
    private final CategoryMapper mapper;
    private final EcommerceSupport support;

    public CategoryService(CategoryMapper mapper, EcommerceSupport support) {
        this.mapper = mapper;
        this.support = support;
    }

    public PageResult<Category> listPublicCategories(int page, int limit) {
        int safeLimit = support.safeLimit(limit);
        List<Category> items = mapper.listCategories(EcommerceSupport.ACTIVE, null, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, mapper.countCategories(EcommerceSupport.ACTIVE, null));
    }

    public PageResult<Category> listAdminCategories(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<Category> items = mapper.listCategories(normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit));
        return support.pageResult(items, page, safeLimit, mapper.countCategories(normalizedStatus, keyword));
    }

    @Transactional
    public UUID createCategory(CategoryRequest request) {
        UUID id = UUID.randomUUID();
        String slug = uniqueSlug(request.slug(), request.name(), null);
        mapper.insertCategory(id, request.parentId(), request.name().trim(), slug, request.description(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.nz(request.sortOrder()), support.adminId());
        return id;
    }

    @Transactional
    public void updateCategory(UUID id, CategoryRequest request) {
        support.require(mapper.findCategoryById(id) != null, "Category not found");
        String slug = uniqueSlug(request.slug(), request.name(), id);
        mapper.updateCategory(id, request.parentId(), request.name().trim(), slug, request.description(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.nz(request.sortOrder()), support.adminId());
    }

    @Transactional
    public void deleteCategory(UUID id) {
        support.require(mapper.findCategoryById(id) != null, "Category not found");
        mapper.softDeleteCategory(id, support.adminId());
    }

    private String uniqueSlug(String slug, String name, UUID exclude) {
        String normalized = support.uniqueSlug(slug, name);
        support.require(mapper.countCategorySlug(normalized, exclude) == 0, "Category slug already exists");
        return normalized;
    }
}
