package com.fss.backend.content.page;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Collection {@code site_pages}: one document per {@link SitePageKey}, keyed by the page key. Pages are never
 * deleted; {@code deleted} is always {@code false} and only exists so the shared "live record" filter used by
 * {@code FileUsageIndex} also matches pages. {@code htmlFilePaths} lists uploaded images used in {@code contentHtml}.
 */
@Document(SitePageDocument.COLLECTION)
public record SitePageDocument(@Id String id, String title, String summary, String contentHtml, List<String> htmlFilePaths,
                               UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt, boolean deleted) {
    public static final String COLLECTION = "site_pages";
}
