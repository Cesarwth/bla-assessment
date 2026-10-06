package com.ballastlane.assessment.domain.exception;

// One message for unknown, expired and already-used tokens: the answer must not tell which one it was.
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException() {
        super("This reset link is invalid or has expired");
    }
}
