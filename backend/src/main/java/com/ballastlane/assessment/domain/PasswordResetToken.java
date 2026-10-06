package com.ballastlane.assessment.domain;

import java.time.Instant;

/** Only the hash of the secret is ever held; the raw token exists in the e-mailed link alone. */
public record PasswordResetToken(
        Long id, Long userId, String tokenHash, Instant expiresAt, Instant usedAt, Instant createdAt) {

    public boolean isUsableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    // The generated toString would print the hash into logs.
    @Override
    public String toString() {
        return "PasswordResetToken[id=" + id + ", userId=" + userId + "]";
    }
}
