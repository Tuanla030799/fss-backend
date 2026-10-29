package com.fss.backend.content.banner;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LandingBannerRequest(String title, String subtitle, String linkUrl, UUID fileId, UUID mobileFileId,
                                   String status, Integer sortOrder, OffsetDateTime startsAt, OffsetDateTime endsAt) {}
