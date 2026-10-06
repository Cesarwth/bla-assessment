package com.ballastlane.assessment.domain;

import com.ballastlane.assessment.domain.exception.InvalidSearchQueryException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a user typed in the catalog filter, normalized once: all digits means "this id", anything else means "name
 * contains this text". Matching is plain substring/equality, so characters such as % or . are never special.
 */
public record PokemonSearchQuery(String text) {

    public static final int MAX_LENGTH = 50;

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Empty when there is nothing to filter by (null or blank). */
    public static Optional<PokemonSearchQuery> of(String raw) {
        String stripped = raw == null ? "" : raw.strip();
        if (stripped.isEmpty()) {
            return Optional.empty();
        }
        if (stripped.length() > MAX_LENGTH) {
            throw new InvalidSearchQueryException("q must be at most " + MAX_LENGTH + " characters");
        }
        // PokeAPI names use hyphens where people type spaces ("mr-mime").
        String text = WHITESPACE.matcher(stripped.toLowerCase(Locale.ROOT)).replaceAll("-");
        return Optional.of(new PokemonSearchQuery(text));
    }

    public boolean isId() {
        return DIGITS.matcher(text).matches();
    }

    public boolean matches(int id, String name) {
        if (isId()) {
            // Compared as text so absurdly long digit strings cannot overflow; leading zeros do not change the id.
            return stripLeadingZeros(text).equals(String.valueOf(id));
        }
        return name != null && name.contains(text);
    }

    private static String stripLeadingZeros(String digits) {
        int first = 0;
        while (first < digits.length() && digits.charAt(first) == '0') {
            first++;
        }
        return digits.substring(first);
    }
}
