package com.ballastlane.assessment.domain.exception;

/** Another request stored the same Pokemon first (unique PokeAPI id). Callers may re-read and retry as an update. */
public class DuplicateStoredPokemonException extends RuntimeException {
    public DuplicateStoredPokemonException(int pokeApiId) {
        super("Pokemon " + pokeApiId + " is already stored");
    }
}
