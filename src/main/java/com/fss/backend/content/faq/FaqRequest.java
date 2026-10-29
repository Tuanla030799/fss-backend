package com.fss.backend.content.faq;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FaqRequest(@NotBlank @Size(max = 300) String question, @NotBlank String answerHtml, Integer sortOrder,
                         String status) {}
