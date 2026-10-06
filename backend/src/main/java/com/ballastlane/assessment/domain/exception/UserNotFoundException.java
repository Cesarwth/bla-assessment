package com.ballastlane.assessment.domain.exception;

/** A valid token whose account has since been deleted. */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("The account no longer exists");
    }
}
