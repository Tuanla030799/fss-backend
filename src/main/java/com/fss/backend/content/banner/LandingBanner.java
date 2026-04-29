package com.fss.backend.content.banner;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LandingBanner(UUID id, String title, String subtitle, String linkUrl, UUID fileId, String imageUrl,
                            String status, Integer sortOrder, OffsetDateTime startsAt, OffsetDateTime endsAt,
                            OffsetDateTime createdAt) {}
