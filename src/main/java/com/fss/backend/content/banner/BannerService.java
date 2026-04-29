package com.fss.backend.content.banner;

import com.fss.backend.shared.ecommerce.EcommerceSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BannerService {
    private final BannerMapper mapper;
    private final EcommerceSupport support;

    public BannerService(BannerMapper mapper, EcommerceSupport support) {
        this.mapper = mapper;
        this.support = support;
    }

    public List<LandingBanner> listPublicBanners() {
        return mapper.listBanners(true, null).stream().map(this::withBannerUrl).toList();
    }

    public List<LandingBanner> listAdminBanners(String status) {
        return mapper.listBanners(false, support.normalizeStatusNullable(status)).stream().map(this::withBannerUrl).toList();
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
        support.require(mapper.findBannerById(id) != null, "Banner not found");
        support.activateFile(request.fileId());
        mapper.updateBanner(id, request.title(), request.subtitle(), request.linkUrl(), request.fileId(),
                support.normalizeStatusDefault(request.status(), EcommerceSupport.ACTIVE), support.nz(request.sortOrder()),
                request.startsAt(), request.endsAt(), support.adminId());
    }

    @Transactional
    public void deleteBanner(UUID id) {
        support.require(mapper.findBannerById(id) != null, "Banner not found");
        mapper.softDeleteBanner(id, support.adminId());
    }

    private LandingBanner withBannerUrl(LandingBanner banner) {
        return banner == null || banner.imageUrl() == null ? banner : new LandingBanner(banner.id(), banner.title(),
                banner.subtitle(), banner.linkUrl(), banner.fileId(), support.publicUrl(banner.imageUrl()), banner.status(),
                banner.sortOrder(), banner.startsAt(), banner.endsAt(), banner.createdAt());
    }
}
