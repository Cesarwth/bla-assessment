package com.ballastlane.assessment.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The password policy itself lives in the use case; here only the shape and sane size limits.
public record ResetPasswordRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 72) String newPassword) {
}
