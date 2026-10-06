package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.ResetPasswordCommand;
import com.ballastlane.assessment.application.port.in.ResetPasswordUseCase;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.PasswordResetTokenRepository;
import com.ballastlane.assessment.application.port.out.ResetTokenGenerator;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.PasswordResetToken;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.InvalidResetTokenException;
import java.time.Clock;
import java.time.Instant;

public class ResetPassword implements ResetPasswordUseCase {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final ResetTokenGenerator generator;
    private final PasswordHasher hasher;
    private final Clock clock;

    public ResetPassword(UserRepository users, PasswordResetTokenRepository tokens, ResetTokenGenerator generator,
            PasswordHasher hasher, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.generator = generator;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Override
    public void reset(ResetPasswordCommand command) {
        // Checked first so a typo in the new password does not burn the single-use link.
        PasswordPolicy.requireStrong(command.newPassword());
        if (command.token() == null || command.token().isBlank()) {
            throw new InvalidResetTokenException();
        }
        Instant now = clock.instant();
        PasswordResetToken token = tokens.findByTokenHash(generator.hash(command.token()))
                .filter(candidate -> candidate.isUsableAt(now))
                .orElseThrow(InvalidResetTokenException::new);
        // Consumed before the password changes: if anything fails afterwards the user simply asks for a new link.
        if (!tokens.markUsed(token.id(), now)) {
            throw new InvalidResetTokenException();
        }
        User user = users.findById(token.userId()).orElseThrow(InvalidResetTokenException::new);
        users.save(new User(user.id(), user.username(), user.email(), hasher.hash(command.newPassword()), user.role()));
        tokens.invalidateUnusedForUser(user.id(), now);
    }
}
