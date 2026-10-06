package com.ballastlane.assessment.domain.exception;

/** The operation is reserved to administrators (e.g. deleting from the shared collection). */
public class AdminRequiredException extends RuntimeException {
    public AdminRequiredException() {
        super("Only an administrator can do this");
    }
}
