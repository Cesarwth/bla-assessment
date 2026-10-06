package com.ballastlane.assessment.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PasswordResetPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(PasswordResetProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsToThirtyMinutes() {
        runner.run(context -> assertThat(context.getBean(PasswordResetProperties.class).ttl()).isEqualTo(Duration.ofMinutes(30)));
    }

    @Test
    void readsTheConfiguredTtl() {
        runner.withPropertyValues("app.password-reset.ttl=45m")
                .run(context -> assertThat(context.getBean(PasswordResetProperties.class).ttl()).isEqualTo(Duration.ofMinutes(45)));
    }

    @Test
    void rejectsANonPositiveTtl() {
        assertThatThrownBy(() -> new PasswordResetProperties(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PasswordResetProperties(Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
    }
}
