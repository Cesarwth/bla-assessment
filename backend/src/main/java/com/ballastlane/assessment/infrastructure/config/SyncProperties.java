package com.ballastlane.assessment.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code maxDuration} is the time budget of one bulk sync; keep it below the reverse proxy's read timeout. */
@ConfigurationProperties("app.sync")
public record SyncProperties(@DefaultValue("90s") Duration maxDuration) {

    public SyncProperties {
        if (maxDuration.isZero() || maxDuration.isNegative()) {
            throw new IllegalArgumentException("app.sync.max-duration must be positive");
        }
    }
}
