package com.ballastlane.assessment.domain.exception;

/** The caller is authenticated but the record belongs to someone else (and the caller is not an admin). */
public class NotOwnerException extends RuntimeException {
    public NotOwnerException(int pokeApiId) {
        super("Pokemon " + pokeApiId + " can only be edited by the user who added it or by an admin");
    }
}
