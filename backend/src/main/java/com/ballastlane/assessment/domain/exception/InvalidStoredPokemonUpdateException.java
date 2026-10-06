package com.ballastlane.assessment.domain.exception;

public class InvalidStoredPokemonUpdateException extends RuntimeException {
    public InvalidStoredPokemonUpdateException(String message) {
        super(message);
    }
}
