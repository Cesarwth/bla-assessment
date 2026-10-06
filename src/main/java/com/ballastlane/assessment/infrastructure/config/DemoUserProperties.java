package com.ballastlane.assessment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.seed.demo-user")
public record DemoUserProperties(boolean enabled, String username, String email, String password) {
}
