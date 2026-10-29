package com.fss.backend.content.banner;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Collection {@code landing_banners}. {@code mobileFileId} is optional and absent on banners saved before it existed. */
@Document(BannerDocument.COLLECTION)
public record BannerDocument(@Id UUID id, String title, String subtitle, String linkUrl, UUID fileId, UUID mobileFileId,
                             String status, Integer sortOrder, Instant startsAt, Instant endsAt, UUID createdBy,
                             UUID updatedBy, Instant createdAt, Instant updatedAt, boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "landing_banners";
}
