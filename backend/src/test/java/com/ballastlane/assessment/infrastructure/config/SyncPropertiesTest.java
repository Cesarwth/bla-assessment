package com.ballastlane.assessment.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SyncPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(SyncProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsToNinetySeconds() {
        runner.run(context -> assertThat(context.getBean(SyncProperties.class).maxDuration()).isEqualTo(Duration.ofSeconds(90)));
    }

    @Test
    void readsTheConfiguredBudget() {
        runner.withPropertyValues("app.sync.max-duration=30s")
                .run(context -> assertThat(context.getBean(SyncProperties.class).maxDuration()).isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void rejectsANonPositiveBudget() {
        assertThatThrownBy(() -> new SyncProperties(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyncProperties(Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }
}
