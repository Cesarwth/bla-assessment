package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.RegisterUserCommand;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.WeakPasswordException;

public class RegisterUser implements RegisterUserUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final PasswordHasher hasher;

    public RegisterUser(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    @Override
    public User register(RegisterUserCommand command) {
        requireStrong(command.rawPassword());
        if (users.existsByUsername(command.username())) {
            throw new UsernameAlreadyExistsException(command.username());
        }
        if (users.existsByEmail(command.email())) {
            throw new EmailAlreadyExistsException(command.email());
        }
        User user = new User(null, command.username(), command.email(), hasher.hash(command.rawPassword()), Role.USER);
        return users.save(user);
    }

    // Policy lives here, not in Bean Validation, so it holds for every entry point (REST, seeder).
    private static void requireStrong(String password) {
        boolean strong = password != null
                && password.length() >= MIN_PASSWORD_LENGTH
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
        if (!strong) {
            throw new WeakPasswordException(
                    "Password must have at least " + MIN_PASSWORD_LENGTH + " characters, with letters and digits");
        }
    }
}
