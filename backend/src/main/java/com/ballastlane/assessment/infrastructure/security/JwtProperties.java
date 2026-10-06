package com.ballastlane.assessment.infrastructure.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, long ttlMinutes) {

    // HS256 needs a 256-bit key; failing at startup beats running with a guessable secret.
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (set the JWT_SECRET env var)");
        }
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("app.jwt.ttl-minutes must be positive");
        }
    }
}
