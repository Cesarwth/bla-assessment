package com.ballastlane.assessment.domain.exception;

public class InvalidSyncRangeException extends RuntimeException {
    public InvalidSyncRangeException(String message) {
        super(message);
    }
}
