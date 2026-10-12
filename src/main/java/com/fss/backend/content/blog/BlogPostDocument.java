package com.fss.backend.content.blog;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Collection {@code blog_posts}. {@code htmlFilePaths} lists uploaded images used in {@code contentHtml}. The legacy
 * Editor.js {@code content_json} was cleared in V13 and is not stored; the API keeps returning {@code "{}"}.
 */
@Document(BlogPostDocument.COLLECTION)
public record BlogPostDocument(@Id UUID id, String title, String slug, String excerpt, String contentHtml,
                               List<String> htmlFilePaths, UUID coverFileId, String status, Instant publishedAt,
                               UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
                               boolean deleted, Instant deletedAt) {
    public static final String COLLECTION = "blog_posts";
}
