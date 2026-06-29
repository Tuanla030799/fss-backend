package com.fss.backend.catalog.brand;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface BrandMapper {
    List<Brand> listBrands(@Param("status") String status, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") int offset);
    long countBrands(@Param("status") String status, @Param("keyword") String keyword);
    Brand findBrandById(@Param("id") UUID id);
    Brand findBrandBySlug(@Param("slug") String slug);
    int countBrandSlug(@Param("slug") String slug, @Param("excludeId") UUID excludeId);
    void insertBrand(@Param("id") UUID id, @Param("name") String name, @Param("slug") String slug,
                     @Param("description") String description, @Param("fileId") UUID fileId,
                     @Param("fileSizeId") UUID fileSizeId,
                     @Param("status") String status, @Param("sortOrder") Integer sortOrder,
                     @Param("adminId") UUID adminId);
    void updateBrand(@Param("id") UUID id, @Param("name") String name, @Param("slug") String slug,
                     @Param("description") String description, @Param("fileId") UUID fileId,
                     @Param("fileSizeId") UUID fileSizeId,
                     @Param("status") String status, @Param("sortOrder") Integer sortOrder,
                     @Param("adminId") UUID adminId);
    void softDeleteBrand(@Param("id") UUID id, @Param("adminId") UUID adminId);
}
