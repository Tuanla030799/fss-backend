package com.fss.backend.content.page;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SitePageRequest(@NotBlank @Size(max = 200) String title, @Size(max = 500) String summary, String contentHtml) {}
