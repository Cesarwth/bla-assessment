package com.ballastlane.assessment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashesWithSaltAndVerifies() {
        String first = hasher.hash("Pikachu123");
        String second = hasher.hash("Pikachu123");

        assertThat(first).isNotEqualTo("Pikachu123").isNotEqualTo(second);
        assertThat(hasher.matches("Pikachu123", first)).isTrue();
        assertThat(hasher.matches("Raichu123", first)).isFalse();
    }
}
