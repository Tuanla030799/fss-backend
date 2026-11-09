package com.fss.backend.content.feedback;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CustomerFeedbackRequest(@NotNull UUID fileId, @Size(max = 120) String customerName,
                                      @Size(max = 1000) String description, Integer sortOrder, String status) {}
