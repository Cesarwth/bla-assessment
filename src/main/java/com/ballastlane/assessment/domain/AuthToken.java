package com.ballastlane.assessment.domain;

import java.time.Instant;

public record AuthToken(String value, Instant expiresAt) {
}
