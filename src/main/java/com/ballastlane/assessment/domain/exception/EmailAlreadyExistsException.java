package com.ballastlane.assessment.domain.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("Email is already registered");
    }
}
