package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.in.RegisterUserCommand;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.WeakPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterUserTest {

    @Mock UserRepository users;
    @Mock PasswordHasher hasher;
    RegisterUser registerUser;

    @BeforeEach
    void setUp() {
        registerUser = new RegisterUser(users, hasher);
    }

    @Test
    void registersNewUserWithHashedPassword() {
        when(hasher.hash("Pikachu123")).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = registerUser.register(new RegisterUserCommand("ash", "ash@pallet.town", "Pikachu123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().passwordHash()).isEqualTo("hashed");
        assertThat(captor.getValue().role()).isEqualTo(Role.USER);
        assertThat(saved.username()).isEqualTo("ash");
    }

    @Test
    void registersTheNormalizedUsernameAndEmail() {
        when(hasher.hash("Pikachu123")).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = registerUser.register(new RegisterUserCommand("  Alice ", "ALICE@Wonder.Land", "Pikachu123"));

        assertThat(saved.username()).isEqualTo("alice");
        assertThat(saved.email()).isEqualTo("alice@wonder.land");
        verify(users).existsByUsername("alice");
        verify(users).existsByEmail("alice@wonder.land");
    }

    @Test
    void failsWhenTheUsernameIsTakenIgnoringCase() {
        when(users.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> registerUser.register(new RegisterUserCommand("ALICE", "a@wonder.land", "Pikachu123")))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        verify(users, never()).save(any());
    }

    @Test
    void rejectsPasswordsBcryptCannotHandleMeasuredInBytesNotCharacters() {
        String multiByte = "é1".repeat(30); // 60 characters but 90 bytes
        assertThatThrownBy(() -> registerUser.register(new RegisterUserCommand("ash", "ash@pallet.town", multiByte)))
                .isInstanceOf(WeakPasswordException.class);
        verify(users, never()).save(any());
    }

    @Test
    void acceptsAPasswordOfExactlyTheMaximumByteLength() {
        String exactly72Bytes = "a1".repeat(36);
        when(hasher.hash(exactly72Bytes)).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(registerUser.register(new RegisterUserCommand("ash", "ash@pallet.town", exactly72Bytes)).username())
                .isEqualTo("ash");
    }

    @Test
    void failsWhenUsernameTaken() {
        when(users.existsByUsername("ash")).thenReturn(true);

        assertThatThrownBy(() -> registerUser.register(new RegisterUserCommand("ash", "ash@pallet.town", "Pikachu123")))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        verify(users, never()).save(any());
    }

    @Test
    void failsWhenEmailTaken() {
        when(users.existsByEmail("ash@pallet.town")).thenReturn(true);

        assertThatThrownBy(() -> registerUser.register(new RegisterUserCommand("ash", "ash@pallet.town", "Pikachu123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "onlyletters", "12345678", ""})
    void failsOnWeakPassword(String password) {
        assertThatThrownBy(() -> registerUser.register(new RegisterUserCommand("ash", "ash@pallet.town", password)))
                .isInstanceOf(WeakPasswordException.class);
        verify(users, never()).save(any());
    }

    @Test
    void failsOnNullPassword() {
        assertThatThrownBy(() -> registerUser.register(new RegisterUserCommand("ash", "ash@pallet.town", null)))
                .isInstanceOf(WeakPasswordException.class);
    }
}
