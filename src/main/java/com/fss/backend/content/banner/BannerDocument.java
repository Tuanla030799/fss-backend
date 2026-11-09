package com.fss.backend.content.banner;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Collection {@code landing_banners}. {@code mobileFileId} and {@code pdfUrl} are optional and absent on banners saved
 * before they existed. {@code linkUrl} is the "Buy now" button, {@code pdfUrl} the "Download a PDF" button.
 */
@Document(BannerDocument.COLLECTION)
public record BannerDocument(@Id UUID id, String title, String subtitle, String linkUrl, String pdfUrl, UUID fileId, UUID mobileFileId,
                             String status, Integer sortOrder, Instant startsAt, Instant endsAt, UUID createdBy,
                             UUID updatedBy, Instant createdAt, Instant updatedAt, boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "landing_banners";
}
