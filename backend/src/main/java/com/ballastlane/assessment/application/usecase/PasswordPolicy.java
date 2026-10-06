package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.domain.exception.WeakPasswordException;

/** One policy for every way a password is set (registration, reset, change), so none of them can be weaker. */
final class PasswordPolicy {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private PasswordPolicy() {
    }

    static void requireStrong(String password) {
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
