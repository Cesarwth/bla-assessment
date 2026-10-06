package com.ballastlane.assessment.infrastructure.web.dto;

import java.time.Instant;

public record AuthResponse(String token, String tokenType, Instant expiresAt) {
}
