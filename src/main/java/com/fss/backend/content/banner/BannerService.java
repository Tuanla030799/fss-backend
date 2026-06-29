package com.fss.backend.content.banner;

import com.fss.backend.common.PageResult;
import com.fss.backend.file.FileReferenceService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BannerService {
    private final BannerMapper mapper;
    private final EcommerceSupport support;
    private final FileReferenceService fileReferenceService;

    public BannerService(BannerMapper mapper, EcommerceSupport support, FileReferenceService fileReferenceService) {
        this.mapper = mapper;
        this.support = support;
        this.fileReferenceService = fileReferenceService;
    }

    public PageResult<LandingBanner> listPublicBanners(int page, int limit) {
        int safeLimit = support.safeLimit(limit);
        List<LandingBanner> items = mapper.listBanners(true, null, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withBannerUrl).toList();
        return support.pageResult(items, page, safeLimit, mapper.countBanners(true, null));
    }

    public PageResult<LandingBanner> listAdminBanners(String status, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<LandingBanner> items = mapper.listBanners(false, normalizedStatus, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withBannerUrl).toList();
        return support.pageResult(items, page, safeLimit, mapper.countBanners(false, normalizedStatus));
    }

    @Transactional
    public UUID createBanner(LandingBannerRequest request) {
        UUID id = UUID.randomUUID();
        support.activateFile(request.fileId());
        mapper.insertBanner(id, request.title(), request.subtitle(), request.linkUrl(), request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.nz(request.sortOrder()),
                request.startsAt(), request.endsAt(), support.adminId());
        return id;
    }

    @Transactional
    public void updateBanner(UUID id, LandingBannerRequest request) {
        LandingBanner existing = mapper.findBannerById(id);
        support.require(existing != null, "Banner not found");
        support.activateFile(request.fileId());
        mapper.updateBanner(id, request.title(), request.subtitle(), request.linkUrl(), request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.nz(request.sortOrder()),
                request.startsAt(), request.endsAt(), support.adminId());
        fileReferenceService.releaseFile(existing.fileId());
    }

    @Transactional
    public void deleteBanner(UUID id) {
        LandingBanner existing = mapper.findBannerById(id);
        support.require(existing != null, "Banner not found");
        mapper.softDeleteBanner(id, support.adminId());
        fileReferenceService.releaseFile(existing.fileId());
    }

    private LandingBanner withBannerUrl(LandingBanner banner) {
        return banner == null || banner.imageUrl() == null ? banner : new LandingBanner(banner.id(), banner.title(),
                banner.subtitle(), banner.linkUrl(), banner.fileId(), support.publicUrl(banner.imageUrl()), banner.status(),
                banner.sortOrder(), banner.startsAt(), banner.endsAt(), banner.createdAt());
    }
}
