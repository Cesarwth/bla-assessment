package com.ballastlane.assessment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void failsFastWhenSecretIsMissingOrShort() {
        assertThatThrownBy(() -> new JwtProperties("", 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(null, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("too-short", 60)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties("x".repeat(32), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsAStrongSecret() {
        assertThatCode(() -> new JwtProperties("x".repeat(32), 60)).doesNotThrowAnyException();
    }
}
