package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.AuthenticateCommand;
import com.ballastlane.assessment.application.port.in.AuthenticateUserUseCase;
import com.ballastlane.assessment.domain.AuthToken;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.InvalidCredentialsException;

public class AuthenticateUser implements AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;

    public AuthenticateUser(UserRepository users, PasswordHasher hasher, TokenIssuer tokens) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    @Override
    public AuthToken authenticate(AuthenticateCommand command) {
        // Such a password can never have been registered (BCrypt rejects it), so don't even ask the hasher.
        if (command.rawPassword() == null || !PasswordLimits.fitsBcrypt(command.rawPassword())) {
            throw new InvalidCredentialsException();
        }
        return users.findByUsername(User.normalizeUsername(command.username()))
                .filter(user -> hasher.matches(command.rawPassword(), user.passwordHash()))
                .map(tokens::issue)
                .orElseThrow(InvalidCredentialsException::new);
    }
}
