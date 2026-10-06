package com.ballastlane.assessment.domain.exception;

public class StoredPokemonNotFoundException extends RuntimeException {
    public StoredPokemonNotFoundException(int pokeApiId) {
        super("Pokemon " + pokeApiId + " is not in the local collection");
    }
}
