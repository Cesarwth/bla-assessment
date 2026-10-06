package com.ballastlane.assessment.domain.exception;

public class InvalidCurrentPasswordException extends RuntimeException {
    public InvalidCurrentPasswordException() {
        super("The current password is incorrect");
    }
}
