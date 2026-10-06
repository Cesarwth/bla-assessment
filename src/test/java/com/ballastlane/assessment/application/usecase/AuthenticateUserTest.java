package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.in.AuthenticateCommand;
import com.ballastlane.assessment.application.port.out.AuthToken;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.InvalidCredentialsException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserTest {

    private static final User ASH = new User(1L, "ash", "ash@pallet.town", "hashed", Role.USER);

    @Mock UserRepository users;
    @Mock PasswordHasher hasher;
    @Mock TokenIssuer tokens;
    AuthenticateUser authenticateUser;

    @BeforeEach
    void setUp() {
        authenticateUser = new AuthenticateUser(users, hasher, tokens);
    }

    @Test
    void returnsTokenOnValidCredentials() {
        AuthToken token = new AuthToken("jwt", Instant.parse("2030-01-01T00:00:00Z"));
        when(users.findByUsername("ash")).thenReturn(Optional.of(ASH));
        when(hasher.matches("Pikachu123", "hashed")).thenReturn(true);
        when(tokens.issue(ASH)).thenReturn(token);

        assertThat(authenticateUser.authenticate(new AuthenticateCommand("ash", "Pikachu123"))).isEqualTo(token);
    }

    @Test
    void failsOnWrongPassword() {
        when(users.findByUsername("ash")).thenReturn(Optional.of(ASH));
        when(hasher.matches("nope", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authenticateUser.authenticate(new AuthenticateCommand("ash", "nope")))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(tokens);
    }

    @Test
    void failsOnUnknownUserWithTheSameExceptionAsWrongPassword() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticateUser.authenticate(new AuthenticateCommand("ghost", "whatever1")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(new InvalidCredentialsException().getMessage());
        verifyNoInteractions(tokens);
    }
}
