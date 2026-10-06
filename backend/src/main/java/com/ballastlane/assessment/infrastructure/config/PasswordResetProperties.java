package com.ballastlane.assessment.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.password-reset")
public record PasswordResetProperties(@DefaultValue("30m") Duration ttl) {

    public PasswordResetProperties {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("app.password-reset.ttl must be positive");
        }
    }
}
