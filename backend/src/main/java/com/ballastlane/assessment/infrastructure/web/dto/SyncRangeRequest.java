package com.ballastlane.assessment.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

// Only presence is checked here; the real limits (from <= to, max size) belong to the use case.
public record SyncRangeRequest(@NotNull Integer from, @NotNull Integer to) {
}
