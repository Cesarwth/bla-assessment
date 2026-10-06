package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.in.ChangePasswordCommand;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.InvalidCurrentPasswordException;
import com.ballastlane.assessment.domain.exception.UserNotFoundException;
import com.ballastlane.assessment.domain.exception.WeakPasswordException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChangePasswordTest {

    private static final User ASH = new User(1L, "ash", "ash@pallet.town", "stored-hash", Role.USER);

    @Mock UserRepository users;
    @Mock PasswordHasher hasher;
    ChangePassword changePassword;

    @BeforeEach
    void setUp() {
        changePassword = new ChangePassword(users, hasher);
        lenient().when(users.findById(1L)).thenReturn(Optional.of(ASH));
    }

    @Test
    void storesTheNewHashWhenTheCurrentPasswordMatches() {
        when(hasher.matches("Pikachu123", "stored-hash")).thenReturn(true);
        when(hasher.hash("Raichu456")).thenReturn("new-hash");

        changePassword.change(new ChangePasswordCommand(1L, "Pikachu123", "Raichu456"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().passwordHash()).isEqualTo("new-hash");
        assertThat(saved.getValue().username()).isEqualTo("ash");
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        when(hasher.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> changePassword.change(new ChangePasswordCommand(1L, "wrong", "Raichu456")))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        verify(users, never()).save(any());
    }

    @Test
    void nullCurrentPasswordIsRejected() {
        assertThatThrownBy(() -> changePassword.change(new ChangePasswordCommand(1L, null, "Raichu456")))
                .isInstanceOf(InvalidCurrentPasswordException.class);
    }

    @Test
    void weakNewPasswordIsRejectedAfterTheCurrentOneIsVerified() {
        when(hasher.matches("Pikachu123", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> changePassword.change(new ChangePasswordCommand(1L, "Pikachu123", "short")))
                .isInstanceOf(WeakPasswordException.class);
        verify(users, never()).save(any());
    }

    @Test
    void reusingTheCurrentPasswordIsRejected() {
        when(hasher.matches("Pikachu123", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> changePassword.change(new ChangePasswordCommand(1L, "Pikachu123", "Pikachu123")))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessage("The new password must be different from the current one");
        verify(users, never()).save(any());
    }

    @Test
    void failsWhenTheAccountNoLongerExists() {
        when(users.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changePassword.change(new ChangePasswordCommand(2L, "Pikachu123", "Raichu456")))
                .isInstanceOf(UserNotFoundException.class);
    }
}
