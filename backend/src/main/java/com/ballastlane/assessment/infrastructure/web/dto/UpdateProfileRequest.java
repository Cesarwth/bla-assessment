package com.ballastlane.assessment.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Every field is optional; the use case requires at least a username or an email. */
public record UpdateProfileRequest(
        @Size(min = 3, max = 50) String username,
        @Email @Size(max = 255) String email,
        @Size(max = 72) String currentPassword) {
}
