package com.fss.backend.content.banner;

import com.fss.backend.common.ErrorCode;
import com.fss.backend.common.PageResult;
import com.fss.backend.file.FileReferenceService;
import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BannerService {
    private final BannerRepository repository;
    private final EcommerceSupport support;
    private final FileReferenceService fileReferenceService;

    public BannerService(BannerRepository repository, EcommerceSupport support, FileReferenceService fileReferenceService) {
        this.repository = repository;
        this.support = support;
        this.fileReferenceService = fileReferenceService;
    }

    public PageResult<LandingBanner> listPublicBanners(int page, int limit) {
        int safeLimit = support.safeLimit(limit);
        List<LandingBanner> items = repository.listBanners(true, null, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withBannerUrl).toList();
        return support.pageResult(items, page, safeLimit, repository.countBanners(true, null));
    }

    public PageResult<LandingBanner> listAdminBanners(String status, int page, int limit) {
        String normalizedStatus = support.normalizeStatusNullable(status);
        int safeLimit = support.safeLimit(limit);
        List<LandingBanner> items = repository.listBanners(false, normalizedStatus, safeLimit, support.offset(page, safeLimit))
                .stream().map(this::withBannerUrl).toList();
        return support.pageResult(items, page, safeLimit, repository.countBanners(false, normalizedStatus));
    }

    @Transactional
    public UUID createBanner(LandingBannerRequest request) {
        UUID id = UUID.randomUUID();
        support.activateFile(request.fileId());
        repository.insertBanner(id, request.title(), request.subtitle(), request.linkUrl(), request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.nz(request.sortOrder()),
                request.startsAt(), request.endsAt(), support.adminId());
        return id;
    }

    @Transactional
    public void updateBanner(UUID id, LandingBannerRequest request) {
        LandingBanner existing = repository.findBannerById(id);
        support.requireFound(existing != null, ErrorCode.BANNER_NOT_FOUND, "Banner not found");
        support.activateFile(request.fileId());
        repository.updateBanner(id, request.title(), request.subtitle(), request.linkUrl(), request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.nz(request.sortOrder()),
                request.startsAt(), request.endsAt(), support.adminId());
        fileReferenceService.releaseFile(existing.fileId());
    }

    @Transactional
    public void deleteBanner(UUID id) {
        LandingBanner existing = repository.findBannerById(id);
        support.requireFound(existing != null, ErrorCode.BANNER_NOT_FOUND, "Banner not found");
        repository.softDeleteBanner(id, support.adminId());
        fileReferenceService.releaseFile(existing.fileId());
    }

    private LandingBanner withBannerUrl(LandingBanner banner) {
        return banner == null || banner.imageUrl() == null ? banner : new LandingBanner(banner.id(), banner.title(),
                banner.subtitle(), banner.linkUrl(), banner.fileId(), support.publicUrl(banner.imageUrl()), banner.status(),
                banner.sortOrder(), banner.startsAt(), banner.endsAt(), banner.createdAt());
    }
}
