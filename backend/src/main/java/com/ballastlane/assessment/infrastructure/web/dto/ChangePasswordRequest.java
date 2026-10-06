package com.ballastlane.assessment.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword, @NotBlank @Size(max = 72) String newPassword) {
}
