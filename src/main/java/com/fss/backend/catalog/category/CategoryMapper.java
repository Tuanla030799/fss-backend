package com.fss.backend.catalog.category;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CategoryMapper {
    List<Category> listCategories(@Param("status") String status, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);
    long countCategories(@Param("status") String status, @Param("keyword") String keyword);
    Category findCategoryById(@Param("id") UUID id);
    Category findCategoryBySlug(@Param("slug") String slug);
    int countCategorySlug(@Param("slug") String slug, @Param("excludeId") UUID excludeId);
    void insertCategory(@Param("id") UUID id, @Param("parentId") UUID parentId, @Param("name") String name,
                        @Param("slug") String slug, @Param("description") String description, @Param("status") String status,
                        @Param("sortOrder") Integer sortOrder, @Param("adminId") UUID adminId);
    void updateCategory(@Param("id") UUID id, @Param("parentId") UUID parentId, @Param("name") String name,
                        @Param("slug") String slug, @Param("description") String description, @Param("status") String status,
                        @Param("sortOrder") Integer sortOrder, @Param("adminId") UUID adminId);
    void softDeleteCategory(@Param("id") UUID id, @Param("adminId") UUID adminId);
}
