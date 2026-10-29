package com.fss.backend.content.banner;

import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code mobileFileId}/{@code mobileImageUrl} are optional: without them the storefront crops the desktop image. */
public record LandingBanner(UUID id, String title, String subtitle, String linkUrl, UUID fileId, String imageUrl,
                            UUID mobileFileId, String mobileImageUrl, String status, Integer sortOrder,
                            OffsetDateTime startsAt, OffsetDateTime endsAt, OffsetDateTime createdAt) {}
