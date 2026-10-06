package com.ballastlane.assessment.infrastructure.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

// Shape check only; the use case re-checks the same limits so they hold for any caller.
public record SyncSelectionRequest(
        @NotEmpty(message = "must contain at least one id")
        @Size(max = 50, message = "must contain at most 50 ids")
        List<@NotNull(message = "must not contain null ids") @Positive(message = "ids must be positive integers") Integer> ids) {
}
