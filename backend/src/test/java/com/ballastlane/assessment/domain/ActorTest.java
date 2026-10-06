package com.ballastlane.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.domain.exception.InvalidUserException;
import org.junit.jupiter.api.Test;

class ActorTest {

    @Test
    void requiresAnIdentity() {
        assertThatThrownBy(() -> new Actor(null, "ash", Role.USER)).isInstanceOf(InvalidUserException.class);
        assertThatThrownBy(() -> new Actor(1L, " ", Role.USER)).isInstanceOf(InvalidUserException.class);
        assertThatThrownBy(() -> new Actor(1L, "ash", null)).isInstanceOf(InvalidUserException.class);
    }
}
