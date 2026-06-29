package com.fss.backend.content.banner;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface BannerMapper {
    List<LandingBanner> listBanners(@Param("publicOnly") boolean publicOnly, @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);
    long countBanners(@Param("publicOnly") boolean publicOnly, @Param("status") String status);
    LandingBanner findBannerById(@Param("id") UUID id);
    void insertBanner(@Param("id") UUID id, @Param("title") String title, @Param("subtitle") String subtitle,
                      @Param("linkUrl") String linkUrl, @Param("fileId") UUID fileId, @Param("status") String status,
                      @Param("sortOrder") Integer sortOrder, @Param("startsAt") OffsetDateTime startsAt,
                      @Param("endsAt") OffsetDateTime endsAt, @Param("adminId") UUID adminId);
    void updateBanner(@Param("id") UUID id, @Param("title") String title, @Param("subtitle") String subtitle,
                      @Param("linkUrl") String linkUrl, @Param("fileId") UUID fileId, @Param("status") String status,
                      @Param("sortOrder") Integer sortOrder, @Param("startsAt") OffsetDateTime startsAt,
                      @Param("endsAt") OffsetDateTime endsAt, @Param("adminId") UUID adminId);
    void softDeleteBanner(@Param("id") UUID id, @Param("adminId") UUID adminId);
}
