package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.in.UpdateProfileCommand;
import com.ballastlane.assessment.application.port.in.UpdatedProfile;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.AuthToken;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.InvalidCurrentPasswordException;
import com.ballastlane.assessment.domain.exception.InvalidUserException;
import com.ballastlane.assessment.domain.exception.UserNotFoundException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateProfileTest {

    private static final User ASH = new User(1L, "ash", "ash@pallet.town", "stored-hash", Role.USER);
    private static final AuthToken TOKEN = new AuthToken("jwt", Instant.parse("2030-01-01T00:00:00Z"));

    @Mock UserRepository users;
    @Mock PasswordHasher hasher;
    @Mock TokenIssuer tokens;
    UpdateProfile updateProfile;

    @BeforeEach
    void setUp() {
        updateProfile = new UpdateProfile(users, hasher, tokens);
        lenient().when(users.findById(1L)).thenReturn(Optional.of(ASH));
    }

    private void updateEchoesAndTokenIsIssued() {
        // the repository answers with the stored row, whose hash is whatever is current in the database
        when(users.updateProfile(eq(1L), anyString(), anyString())).thenAnswer(invocation ->
                new User(1L, invocation.getArgument(1), invocation.getArgument(2), "stored-hash", Role.USER));
        when(tokens.issue(any(User.class))).thenReturn(TOKEN);
    }

    @Test
    void changesTheUsernameWithoutAPasswordAndIssuesATokenForTheNewName() {
        updateEchoesAndTokenIsIssued();

        UpdatedProfile updatedProfile = updateProfile.update(new UpdateProfileCommand(1L, "  Red ", null, null));

        verify(users).updateProfile(1L, "red", "ash@pallet.town");
        // never the whole-row save: it would write back the password hash read earlier
        verify(users, never()).save(any());
        assertThat(updatedProfile.user().username()).isEqualTo("red");
        assertThat(updatedProfile.token()).isEqualTo(TOKEN);
        verify(tokens).issue(updatedProfile.user());
    }

    @Test
    void changesTheEmailWhenTheCurrentPasswordMatches() {
        updateEchoesAndTokenIsIssued();
        when(hasher.matches("Pikachu123", "stored-hash")).thenReturn(true);

        UpdatedProfile updatedProfile = updateProfile.update(new UpdateProfileCommand(1L, null, " NEW@Pallet.Town", "Pikachu123"));

        assertThat(updatedProfile.user().email()).isEqualTo("new@pallet.town");
        assertThat(updatedProfile.user().username()).isEqualTo("ash");
    }

    @Test
    void changingTheEmailWithAWrongPasswordFailsBeforeAnyUniquenessCheck() {
        when(hasher.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> updateProfile.update(new UpdateProfileCommand(1L, null, "new@pallet.town", "wrong")))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        verify(users, never()).existsByEmail(any());
        verify(users, never()).updateProfile(any(), any(), any());
    }

    @Test
    void changingTheEmailWithoutAPasswordFails() {
        assertThatThrownBy(() -> updateProfile.update(new UpdateProfileCommand(1L, null, "new@pallet.town", null)))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        verify(users, never()).updateProfile(any(), any(), any());
    }

    @Test
    void aPasswordBcryptCannotHandleIsAWrongPasswordNotAHasherError() {
        assertThatThrownBy(() -> updateProfile.update(new UpdateProfileCommand(1L, null, "new@pallet.town", "é".repeat(40))))
                .isInstanceOf(InvalidCurrentPasswordException.class);
        verify(hasher, never()).matches(any(), any());
    }

    @Test
    void resubmittingTheCurrentEmailIsNotAChangeAndNeedsNoPassword() {
        updateEchoesAndTokenIsIssued();

        UpdatedProfile updatedProfile = updateProfile.update(new UpdateProfileCommand(1L, "red", "ASH@pallet.town", null));

        assertThat(updatedProfile.user().email()).isEqualTo("ash@pallet.town");
        verify(users, never()).existsByEmail(any());
    }

    @Test
    void usernameTakenByAnotherAccountIsAConflict() {
        when(users.existsByUsername("red")).thenReturn(true);

        assertThatThrownBy(() -> updateProfile.update(new UpdateProfileCommand(1L, "Red", null, null)))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        verify(users, never()).updateProfile(any(), any(), any());
    }

    @Test
    void emailTakenByAnotherAccountIsAConflict() {
        when(hasher.matches("Pikachu123", "stored-hash")).thenReturn(true);
        when(users.existsByEmail("red@pallet.town")).thenReturn(true);

        assertThatThrownBy(() -> updateProfile.update(new UpdateProfileCommand(1L, null, "red@pallet.town", "Pikachu123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(users, never()).updateProfile(any(), any(), any());
    }

    @Test
    void keepingTheSameUsernameDoesNotCollideWithItself() {
        updateEchoesAndTokenIsIssued();

        updateProfile.update(new UpdateProfileCommand(1L, "ASH", null, null));

        verify(users, never()).existsByUsername(any());
    }

    @Test
    void requiresAtLeastOneField() {
        assertThatThrownBy(() -> updateProfile.update(new UpdateProfileCommand(1L, null, null, "Pikachu123")))
                .isInstanceOf(InvalidUserException.class);
    }

    @Test
    void rejectsAnInvalidNewUsername() {
        assertThatThrownBy(() -> updateProfile.update(new UpdateProfileCommand(1L, "ab", null, null)))
                .isInstanceOf(InvalidUserException.class);
        verify(users, never()).updateProfile(any(), any(), any());
    }

    @Test
    void failsWhenTheAccountNoLongerExists() {
        when(users.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateProfile.update(new UpdateProfileCommand(2L, "red", null, null)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
