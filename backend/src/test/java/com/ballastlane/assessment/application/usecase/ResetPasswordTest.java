package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.in.ResetPasswordCommand;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.PasswordResetTokenRepository;
import com.ballastlane.assessment.application.port.out.ResetTokenGenerator;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.PasswordResetToken;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.InvalidResetTokenException;
import com.ballastlane.assessment.domain.exception.WeakPasswordException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResetPasswordTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final User ASH = new User(1L, "ash", "ash@pallet.town", "old-hash", Role.USER);

    @Mock UserRepository users;
    @Mock PasswordResetTokenRepository tokens;
    @Mock ResetTokenGenerator generator;
    @Mock PasswordHasher hasher;
    ResetPassword resetPassword;

    @BeforeEach
    void setUp() {
        resetPassword = new ResetPassword(users, tokens, generator, hasher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PasswordResetToken token(Instant expiresAt, Instant usedAt) {
        return new PasswordResetToken(7L, 1L, "hash-of-token", expiresAt, usedAt, NOW.minusSeconds(60));
    }

    @Test
    void validTokenStoresTheNewHashConsumesTheTokenAndInvalidatesTheOthers() {
        when(generator.hash("raw")).thenReturn("hash-of-token");
        when(tokens.findByTokenHash("hash-of-token")).thenReturn(Optional.of(token(NOW.plusSeconds(1), null)));
        when(tokens.markUsed(7L, NOW)).thenReturn(true);
        when(users.findById(1L)).thenReturn(Optional.of(ASH));
        when(hasher.hash("Brand9New")).thenReturn("new-hash");

        resetPassword.reset(new ResetPasswordCommand("raw", "Brand9New"));

        // The password is written last: a failure after it cannot leave other reset links valid.
        InOrder order = inOrder(tokens, users);
        order.verify(tokens).markUsed(7L, NOW);
        order.verify(tokens).invalidateUnusedForUser(1L, NOW);
        order.verify(users).updatePasswordHash(1L, "new-hash");
        verify(users, never()).save(any());
    }

    @Test
    void unknownTokenIsRejected() {
        when(generator.hash("raw")).thenReturn("hash-of-token");
        when(tokens.findByTokenHash("hash-of-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resetPassword.reset(new ResetPasswordCommand("raw", "Brand9New")))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("This reset link is invalid or has expired");
        verify(users, never()).updatePasswordHash(any(), any());
    }

    @Test
    void expiredTokenIsRejected() {
        when(generator.hash("raw")).thenReturn("hash-of-token");
        when(tokens.findByTokenHash("hash-of-token")).thenReturn(Optional.of(token(NOW, null)));

        assertThatThrownBy(() -> resetPassword.reset(new ResetPasswordCommand("raw", "Brand9New")))
                .isInstanceOf(InvalidResetTokenException.class);
        verify(tokens, never()).markUsed(any(), any());
        verify(users, never()).updatePasswordHash(any(), any());
    }

    @Test
    void alreadyUsedTokenIsRejected() {
        when(generator.hash("raw")).thenReturn("hash-of-token");
        when(tokens.findByTokenHash("hash-of-token")).thenReturn(Optional.of(token(NOW.plusSeconds(60), NOW.minusSeconds(5))));

        assertThatThrownBy(() -> resetPassword.reset(new ResetPasswordCommand("raw", "Brand9New")))
                .isInstanceOf(InvalidResetTokenException.class);
        verify(users, never()).updatePasswordHash(any(), any());
    }

    @Test
    void aTokenConsumedByAConcurrentRequestIsRejected() {
        when(generator.hash("raw")).thenReturn("hash-of-token");
        when(tokens.findByTokenHash("hash-of-token")).thenReturn(Optional.of(token(NOW.plusSeconds(60), null)));
        when(tokens.markUsed(7L, NOW)).thenReturn(false);

        assertThatThrownBy(() -> resetPassword.reset(new ResetPasswordCommand("raw", "Brand9New")))
                .isInstanceOf(InvalidResetTokenException.class);
        verify(users, never()).updatePasswordHash(any(), any());
    }

    @Test
    void tokenOfAVanishedUserIsRejected() {
        when(generator.hash("raw")).thenReturn("hash-of-token");
        when(tokens.findByTokenHash("hash-of-token")).thenReturn(Optional.of(token(NOW.plusSeconds(60), null)));
        when(tokens.markUsed(7L, NOW)).thenReturn(true);
        when(users.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resetPassword.reset(new ResetPasswordCommand("raw", "Brand9New")))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void weakPasswordIsRejectedWithoutConsumingTheToken() {
        assertThatThrownBy(() -> resetPassword.reset(new ResetPasswordCommand("raw", "short")))
                .isInstanceOf(WeakPasswordException.class);
        verifyNoInteractions(tokens, users);
    }

    @Test
    void nullTokenIsRejected() {
        assertThatThrownBy(() -> resetPassword.reset(new ResetPasswordCommand(null, "Brand9New")))
                .isInstanceOf(InvalidResetTokenException.class);
    }
}
