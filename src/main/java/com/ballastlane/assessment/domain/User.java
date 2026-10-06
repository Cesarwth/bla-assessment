package com.ballastlane.assessment.domain;

import com.ballastlane.assessment.domain.exception.InvalidUserException;
import java.util.Locale;
import java.util.regex.Pattern;

public record User(Long id, String username, String email, String passwordHash, Role role) {

    private static final int MIN_USERNAME_LENGTH = 3;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public User {
        // Stored in one canonical form so "Alice" / "alice " / "ALICE" cannot become lookalike accounts.
        username = normalizeUsername(username);
        email = normalizeEmail(email);
        if (username.length() < MIN_USERNAME_LENGTH) {
            throw new InvalidUserException("Username must have at least " + MIN_USERNAME_LENGTH + " characters");
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new InvalidUserException("Email is not valid");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new InvalidUserException("Password hash is required");
        }
        if (role == null) {
            throw new InvalidUserException("Role is required");
        }
    }

    public static String normalizeUsername(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    public static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    // The generated toString would print the hash into logs.
    @Override
    public String toString() {
        return "User[id=" + id + ", username=" + username + ", role=" + role + "]";
    }
}
