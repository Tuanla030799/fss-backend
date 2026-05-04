package com.fss.backend.content.blog;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BlogPostRequest(@NotBlank String title, String slug, String excerpt, String contentJson,
                              UUID coverFileId, String status, OffsetDateTime publishedAt) {}
