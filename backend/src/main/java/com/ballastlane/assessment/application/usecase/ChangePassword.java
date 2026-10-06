package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.ChangePasswordCommand;
import com.ballastlane.assessment.application.port.in.ChangePasswordUseCase;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.InvalidCurrentPasswordException;
import com.ballastlane.assessment.domain.exception.UserNotFoundException;
import com.ballastlane.assessment.domain.exception.WeakPasswordException;

public class ChangePassword implements ChangePasswordUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;

    public ChangePassword(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    @Override
    public void change(ChangePasswordCommand command) {
        User user = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
        String currentPassword = command.currentPassword();
        if (currentPassword == null || !PasswordLimits.fitsBcrypt(currentPassword)
                || !hasher.matches(currentPassword, user.passwordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        PasswordPolicy.requireStrong(command.newPassword());
        if (command.newPassword().equals(currentPassword)) {
            throw new WeakPasswordException("The new password must be different from the current one");
        }
        users.save(new User(user.id(), user.username(), user.email(), hasher.hash(command.newPassword()), user.role()));
    }
}
