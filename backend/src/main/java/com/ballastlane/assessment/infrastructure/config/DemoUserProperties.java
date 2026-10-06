package com.ballastlane.assessment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Demo-only accounts. The admin is only seeded when an admin password is provided (no default on purpose). */
@ConfigurationProperties("app.seed.demo-user")
public record DemoUserProperties(
        boolean enabled,
        String username,
        String email,
        String password,
        String adminUsername,
        String adminEmail,
        String adminPassword) {
}
