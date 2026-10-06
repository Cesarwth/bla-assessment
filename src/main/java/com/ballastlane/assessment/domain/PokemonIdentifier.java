package com.ballastlane.assessment.domain;

import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import java.util.Locale;
import java.util.regex.Pattern;

/** A PokeAPI id or name, normalized. Single place for the rule so every use case treats input the same way. */
public record PokemonIdentifier(String value) {

    // Ids and PokeAPI names only use these characters; rejecting the rest keeps odd input out of upstream URLs.
    private static final Pattern VALID = Pattern.compile("[a-z0-9-]+");

    public PokemonIdentifier {
        value = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new InvalidPokemonIdentifierException("Pokemon id or name is not valid");
        }
    }

    public static PokemonIdentifier of(String raw) {
        return new PokemonIdentifier(raw);
    }

    /** Numeric ids (e.g. from a URL path) must be positive; PokeAPI ids start at 1. */
    public static int requirePositive(int id) {
        if (id < 1) {
            throw new InvalidPokemonIdentifierException("Pokemon id must be a positive number");
        }
        return id;
    }
}
