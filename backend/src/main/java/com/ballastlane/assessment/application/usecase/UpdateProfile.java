package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.UpdateProfileCommand;
import com.ballastlane.assessment.application.port.in.UpdateProfileUseCase;
import com.ballastlane.assessment.application.port.in.UpdatedProfile;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.InvalidCurrentPasswordException;
import com.ballastlane.assessment.domain.exception.InvalidUserException;
import com.ballastlane.assessment.domain.exception.UserNotFoundException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;

public class UpdateProfile implements UpdateProfileUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;

    public UpdateProfile(UserRepository users, PasswordHasher hasher, TokenIssuer tokens) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    @Override
    public UpdatedProfile update(UpdateProfileCommand command) {
        if (command.username() == null && command.email() == null) {
            throw new InvalidUserException("Provide a username or an email to update");
        }
        User current = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
        String username = command.username() == null ? current.username() : User.normalizeUsername(command.username());
        String email = command.email() == null ? current.email() : User.normalizeEmail(command.email());
        boolean usernameChanged = !username.equals(current.username());
        boolean emailChanged = !email.equals(current.email());

        // The e-mail is where password resets go: whoever holds only a stolen session must not be able to redirect it.
        // Checked before the uniqueness lookup so a caller without the password cannot probe which e-mails are taken.
        if (emailChanged) {
            requireCurrentPassword(command.currentPassword(), current);
        }
        if (usernameChanged && users.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
        if (emailChanged && users.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User saved = users.save(new User(current.id(), username, email, current.passwordHash(), current.role()));
        return new UpdatedProfile(saved, tokens.issue(saved));
    }

    private void requireCurrentPassword(String candidate, User user) {
        if (candidate == null || !PasswordLimits.fitsBcrypt(candidate) || !hasher.matches(candidate, user.passwordHash())) {
            throw new InvalidCurrentPasswordException();
        }
    }
}
