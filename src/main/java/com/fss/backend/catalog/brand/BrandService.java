package com.fss.backend.catalog.brand;

import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BrandService {
    private final BrandMapper mapper;
    private final EcommerceSupport support;

    public BrandService(BrandMapper mapper, EcommerceSupport support) {
        this.mapper = mapper;
        this.support = support;
    }

    public List<Brand> listPublicBrands(String keyword) {
        return mapper.listBrands(EcommerceSupport.ACTIVE, keyword).stream().map(this::withBrandUrl).toList();
    }

    public Brand getPublicBrand(String slug) {
        Brand brand = mapper.findBrandBySlug(slug);
        support.require(brand != null && EcommerceSupport.ACTIVE.equals(brand.status()), "Brand not found");
        return withBrandUrl(brand);
    }

    public List<Brand> listAdminBrands(String status, String keyword) {
        return mapper.listBrands(support.normalizeStatusNullable(status), keyword).stream().map(this::withBrandUrl).toList();
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
        mapper.insertBrand(id, request.name().trim(), uniqueSlug(request.slug(), request.name(), null),
                request.description(), request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
        return id;
    }

    @Transactional
    public void updateBrand(UUID id, BrandRequest request) {
        support.require(mapper.findBrandById(id) != null, "Brand not found");
        support.activateFile(request.fileId());
        mapper.updateBrand(id, request.name().trim(), uniqueSlug(request.slug(), request.name(), id),
                request.description(), request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE),
                support.nz(request.sortOrder()), support.adminId());
    }

    @Transactional
    public void deleteBrand(UUID id) {
        support.require(mapper.findBrandById(id) != null, "Brand not found");
        mapper.softDeleteBrand(id, support.adminId());
    }

    private String uniqueSlug(String slug, String name, UUID exclude) {
        String normalized = support.uniqueSlug(slug, name);
        support.require(mapper.countBrandSlug(normalized, exclude) == 0, "Brand slug already exists");
        return normalized;
    }

    private Brand withBrandUrl(Brand brand) {
        return brand == null || brand.imageUrl() == null ? brand : new Brand(brand.id(), brand.name(), brand.slug(),
                brand.description(), brand.fileId(), support.publicUrl(brand.imageUrl()), brand.status(),
                brand.sortOrder(), brand.createdAt());
    }
}
