package com.ballastlane.assessment.application.usecase;

import java.nio.charset.StandardCharsets;

/** BCrypt only accepts 72 BYTES (not characters); longer input throws instead of being truncated. */
final class PasswordLimits {

    static final int MAX_BYTES = 72;

    private PasswordLimits() {
    }

    static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
