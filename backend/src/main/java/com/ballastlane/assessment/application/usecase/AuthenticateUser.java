package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.AuthenticateCommand;
import com.ballastlane.assessment.application.port.in.AuthenticateUserUseCase;
import com.ballastlane.assessment.domain.AuthToken;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.InvalidCredentialsException;
import java.util.Optional;

public class AuthenticateUser implements AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;
    private String dummyHash; // created lazily: hashing is slow and only the unknown-user path needs it

    public AuthenticateUser(UserRepository users, PasswordHasher hasher, TokenIssuer tokens) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    private synchronized String dummyHash() {
        if (dummyHash == null) {
            dummyHash = hasher.hash("timing-equalizer-not-a-real-password");
        }
        return dummyHash;
    }

    @Override
    public AuthToken authenticate(AuthenticateCommand command) {
        // Such a password can never have been registered (BCrypt rejects it), so don't even ask the hasher.
        if (command.rawPassword() == null || !PasswordLimits.fitsBcrypt(command.rawPassword())) {
            throw new InvalidCredentialsException();
        }
        Optional<User> user = users.findByUsername(User.normalizeUsername(command.username()));
        if (user.isEmpty()) {
            // Spend the same time as a real check so response time does not reveal which usernames exist.
            hasher.matches(command.rawPassword(), dummyHash());
            throw new InvalidCredentialsException();
        }
        if (!hasher.matches(command.rawPassword(), user.get().passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(user.get());
    }
}
