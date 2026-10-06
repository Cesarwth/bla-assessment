package com.ballastlane.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.domain.exception.InvalidUserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ab"})
    void rejectsBlankOrTooShortUsername(String username) {
        assertThatThrownBy(() -> new User(null, username, "ash@pallet.town", "hash", Role.USER))
                .isInstanceOf(InvalidUserException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-an-email", "a@", "@b.com", "a b@c.com"})
    void rejectsInvalidEmail(String email) {
        assertThatThrownBy(() -> new User(null, "ash", email, "hash", Role.USER))
                .isInstanceOf(InvalidUserException.class);
    }

    @Test
    void rejectsMissingPasswordHashOrRole() {
        assertThatThrownBy(() -> new User(null, "ash", "ash@pallet.town", "", Role.USER))
                .isInstanceOf(InvalidUserException.class);
        assertThatThrownBy(() -> new User(null, "ash", "ash@pallet.town", "hash", null))
                .isInstanceOf(InvalidUserException.class);
    }

    @Test
    void normalizesUsernameAndEmailSoLookalikesAreTheSameAccount() {
        User user = new User(null, "  Ash ", "  ASH@Pallet.Town ", "hash", Role.USER);

        assertThat(user.username()).isEqualTo("ash");
        assertThat(user.email()).isEqualTo("ash@pallet.town");
    }

    @Test
    void toStringNeverExposesThePasswordHash() {
        User user = new User(1L, "ash", "ash@pallet.town", "secret-hash", Role.USER);

        assertThat(user.toString()).doesNotContain("secret-hash");
    }
}
