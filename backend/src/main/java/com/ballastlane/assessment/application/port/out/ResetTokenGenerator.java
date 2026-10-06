package com.ballastlane.assessment.application.port.out;

/** Behind a port so use cases stay deterministic in tests. */
public interface ResetTokenGenerator {
    /** A new unguessable token, safe to put in a URL. */
    String generate();

    /** The form in which a token is stored and looked up; the raw token is never persisted. */
    String hash(String rawToken);
}
