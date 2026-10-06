package com.ballastlane.assessment.domain.exception;

public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(String username) {
        super("Username is already taken");
    }
}
