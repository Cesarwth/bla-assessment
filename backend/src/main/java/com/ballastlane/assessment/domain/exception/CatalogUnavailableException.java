package com.ballastlane.assessment.domain.exception;

/** The external Pokemon catalog failed or timed out; {@code timeout} lets callers tell 502 from 504. */
public class CatalogUnavailableException extends RuntimeException {

    private final boolean timeout;

    public CatalogUnavailableException(String message, boolean timeout, Throwable cause) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
