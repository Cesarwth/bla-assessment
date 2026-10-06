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
        String username = User.normalizeUsername(command.username());
        String email = User.normalizeEmail(command.email());
        if (users.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = new User(null, username, email, hasher.hash(command.rawPassword()), Role.USER);
        return users.save(user);
    }

    // Policy lives here, not in Bean Validation, so it holds for every entry point (REST, seeder).
    private static void requireStrong(String password) {
        boolean strong = password != null
                && password.length() >= MIN_PASSWORD_LENGTH
                && PasswordLimits.fitsBcrypt(password)
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
        if (!strong) {
            throw new WeakPasswordException(
                    "Password must have " + MIN_PASSWORD_LENGTH + " to " + PasswordLimits.MAX_BYTES
                            + " bytes, with letters and digits");
        }
    }
}
