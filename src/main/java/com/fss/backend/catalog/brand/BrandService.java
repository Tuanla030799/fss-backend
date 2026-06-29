package com.fss.backend.catalog.brand;

import com.fss.backend.common.PageResult;
import com.fss.backend.file.FileReferenceService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BrandService {
    private final BrandMapper mapper;
    private final EcommerceSupport support;
    private final FileReferenceService fileReferenceService;

    public BrandService(BrandMapper mapper, EcommerceSupport support, FileReferenceService fileReferenceService) {
        this.mapper = mapper;
        this.support = support;
        this.fileReferenceService = fileReferenceService;
    }

    public PageResult<Brand> listPublicBrands(String keyword, int page, int limit) {
        int safeLimit = support.safeLimit(limit);
        List<Brand> items = mapper.listBrands(EcommerceSupport.ACTIVE, keyword, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withBrandUrl).toList();
        return support.pageResult(items, page, safeLimit, mapper.countBrands(EcommerceSupport.ACTIVE, keyword));
    }

    public Brand getPublicBrand(String slug) {
        Brand brand = mapper.findBrandBySlug(slug);
        support.require(brand != null && EcommerceSupport.ACTIVE.equals(brand.status()), "Brand not found");
        return withBrandUrl(brand);
    }

    public PageResult<Brand> listAdminBrands(String status, String keyword, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<Brand> items = mapper.listBrands(normalizedStatus, keyword, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withBrandUrl).toList();
        return support.pageResult(items, page, safeLimit, mapper.countBrands(normalizedStatus, keyword));
    }

    public Brand getAdminBrand(UUID id) {
        Brand brand = mapper.findBrandById(id);
        support.require(brand != null, "Brand not found");
        return withBrandUrl(brand);
    }

    @Transactional
    public UUID createBrand(BrandRequest request) {
        UUID id = UUID.randomUUID();
        support.activateFile(request.fileId());
        support.activateFile(request.fileSizeId());
        mapper.insertBrand(id, request.name().trim(), uniqueSlug(request.slug(), request.name(), null),
                request.description(), request.fileId(), request.fileSizeId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
        return id;
    }

    @Transactional
    public void updateBrand(UUID id, BrandRequest request) {
        Brand existing = mapper.findBrandById(id);
        support.require(existing != null, "Brand not found");
        support.activateFile(request.fileId());
        support.activateFile(request.fileSizeId());
        mapper.updateBrand(id, request.name().trim(), uniqueSlug(request.slug(), request.name(), id),
                request.description(), request.fileId(), request.fileSizeId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
        fileReferenceService.releaseFile(existing.fileId());
        fileReferenceService.releaseFile(existing.fileSizeId());
    }

    @Transactional
    public void deleteBrand(UUID id) {
        Brand existing = mapper.findBrandById(id);
        support.require(existing != null, "Brand not found");
        mapper.softDeleteBrand(id, support.adminId());
        fileReferenceService.releaseFile(existing.fileId());
        fileReferenceService.releaseFile(existing.fileSizeId());
    }

    private String uniqueSlug(String slug, String name, UUID exclude) {
        String normalized = support.uniqueSlug(slug, name);
        support.require(mapper.countBrandSlug(normalized, exclude) == 0, "Brand slug already exists");
        return normalized;
    }

    private Brand withBrandUrl(Brand brand) {
        return brand == null ? null : new Brand(
                brand.id(),
                brand.name(),
                brand.slug(),
                brand.description(),
                brand.fileId(),
                support.publicUrl(brand.imageUrl()),
                brand.fileSizeId(),
                support.publicUrl(brand.sizeGuideImageUrl()),
                brand.status(),
                brand.sortOrder(),
                brand.createdAt()
        );
    }
}
