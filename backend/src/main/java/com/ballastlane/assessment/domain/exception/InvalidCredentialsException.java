package com.ballastlane.assessment.domain.exception;

// One message for "unknown user" and "wrong password" so the API cannot be used to enumerate users.
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
