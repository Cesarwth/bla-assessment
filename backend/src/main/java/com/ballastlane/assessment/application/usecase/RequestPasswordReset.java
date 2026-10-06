package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.RequestPasswordResetUseCase;
import com.ballastlane.assessment.application.port.out.PasswordResetNotifier;
import com.ballastlane.assessment.application.port.out.PasswordResetTokenRepository;
import com.ballastlane.assessment.application.port.out.ResetTokenGenerator;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.PasswordResetToken;
import com.ballastlane.assessment.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class RequestPasswordReset implements RequestPasswordResetUseCase {

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final ResetTokenGenerator generator;
    private final PasswordResetNotifier notifier;
    private final Clock clock;
    private final Duration ttl;

    public RequestPasswordReset(UserRepository users, PasswordResetTokenRepository tokens, ResetTokenGenerator generator,
            PasswordResetNotifier notifier, Clock clock, Duration ttl) {
        this.users = users;
        this.tokens = tokens;
        this.generator = generator;
        this.notifier = notifier;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public void request(String email) {
        // An unknown address returns exactly like a known one: the caller must not learn which accounts exist.
        users.findByEmail(User.normalizeEmail(email)).ifPresent(this::issueFor);
    }

    private void issueFor(User user) {
        Instant now = clock.instant();
        // Only the newest link works: an older e-mail sitting in someone's inbox must not stay a valid way in.
        tokens.invalidateUnusedForUser(user.id(), now);
        String raw = generator.generate();
        tokens.save(new PasswordResetToken(null, user.id(), generator.hash(raw), now.plus(ttl), null, now));
        notifier.sendResetLink(user.email(), raw);
    }
}
