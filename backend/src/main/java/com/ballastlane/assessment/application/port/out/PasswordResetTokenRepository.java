package com.ballastlane.assessment.application.port.out;

import com.ballastlane.assessment.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Atomic single-use guard: true only for the one caller that flips an unused token to used. */
    boolean markUsed(Long tokenId, Instant at);

    /** Marks every still-unused token of the user as used, so older links stop working. */
    void invalidateUnusedForUser(Long userId, Instant at);
}
