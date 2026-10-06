package com.ballastlane.assessment.application.port.out;

import java.time.Instant;

public record AuthToken(String value, Instant expiresAt) {
}
