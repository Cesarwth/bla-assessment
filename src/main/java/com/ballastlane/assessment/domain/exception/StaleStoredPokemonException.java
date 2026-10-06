package com.ballastlane.assessment.domain.exception;

/** The record changed since it was read; the caller should reload and retry. */
public class StaleStoredPokemonException extends RuntimeException {
    public StaleStoredPokemonException(int pokeApiId) {
        super("Pokemon " + pokeApiId + " was modified by someone else");
    }
}
