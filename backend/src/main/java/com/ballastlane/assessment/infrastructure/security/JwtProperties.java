package com.ballastlane.assessment.infrastructure.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, long ttlMinutes) {

    // HS256 needs a 256-bit key; failing at startup beats running with a guessable secret.
    private static final int MIN_SECRET_BYTES = 32;
    private static final String PLACEHOLDER_PREFIX = "change-me";

    public JwtProperties {
        if (secret != null && secret.toLowerCase(java.util.Locale.ROOT).startsWith(PLACEHOLDER_PREFIX)) {
            // The example file ships this value: it is public, so it must never sign real tokens.
            throw new IllegalArgumentException(
                    "app.jwt.secret is still the placeholder from .env.example; set JWT_SECRET to a random value (openssl rand -hex 32)");
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (set the JWT_SECRET env var; e.g. openssl rand -hex 32)");
        }
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("app.jwt.ttl-minutes must be positive");
        }
    }
}
