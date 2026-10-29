package com.fss.backend.content.page;

import java.time.OffsetDateTime;

public record SitePage(String key, String title, String summary, String contentHtml,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
