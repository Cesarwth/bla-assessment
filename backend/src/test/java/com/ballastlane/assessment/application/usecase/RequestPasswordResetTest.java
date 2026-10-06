package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.PasswordResetNotifier;
import com.ballastlane.assessment.application.port.out.PasswordResetTokenRepository;
import com.ballastlane.assessment.application.port.out.ResetTokenGenerator;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.PasswordResetToken;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestPasswordResetTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final User ASH = new User(1L, "ash", "ash@pallet.town", "hashed", Role.USER);

    @Mock UserRepository users;
    @Mock PasswordResetTokenRepository tokens;
    @Mock ResetTokenGenerator generator;
    @Mock PasswordResetNotifier notifier;
    RequestPasswordReset requestReset;

    @BeforeEach
    void setUp() {
        requestReset = new RequestPasswordReset(users, tokens, generator, notifier,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(30));
    }

    @Test
    void unknownEmailReturnsNormallyAndSendsNothing() {
        when(users.findByEmail("ghost@nowhere.io")).thenReturn(Optional.empty());

        requestReset.request("ghost@nowhere.io");

        verifyNoInteractions(tokens, generator, notifier);
    }

    @Test
    void knownEmailStoresOnlyTheHashWithTheConfiguredExpiryAndSendsTheRawToken() {
        when(users.findByEmail("ash@pallet.town")).thenReturn(Optional.of(ASH));
        when(generator.generate()).thenReturn("raw-token");
        when(generator.hash("raw-token")).thenReturn("hash-of-token");

        requestReset.request("ash@pallet.town");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().userId()).isEqualTo(1L);
        assertThat(saved.getValue().tokenHash()).isEqualTo("hash-of-token");
        assertThat(saved.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(saved.getValue().createdAt()).isEqualTo(NOW);
        assertThat(saved.getValue().usedAt()).isNull();
        verify(notifier).sendResetLink("ash@pallet.town", "raw-token");
    }

    @Test
    void previousUnusedTokensAreInvalidatedBeforeTheNewOneIsStored() {
        when(users.findByEmail("ash@pallet.town")).thenReturn(Optional.of(ASH));
        when(generator.generate()).thenReturn("raw-token");
        when(generator.hash("raw-token")).thenReturn("hash-of-token");

        requestReset.request("ash@pallet.town");

        InOrder order = inOrder(tokens, notifier);
        order.verify(tokens).invalidateUnusedForUser(1L, NOW);
        order.verify(tokens).save(any(PasswordResetToken.class));
        order.verify(notifier).sendResetLink("ash@pallet.town", "raw-token");
    }

    @Test
    void theEmailIsNormalizedBeforeTheLookup() {
        when(users.findByEmail("ash@pallet.town")).thenReturn(Optional.empty());

        requestReset.request("  ASH@Pallet.Town ");

        verify(users).findByEmail("ash@pallet.town");
    }
}
