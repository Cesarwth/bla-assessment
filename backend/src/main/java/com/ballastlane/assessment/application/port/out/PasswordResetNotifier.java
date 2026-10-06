package com.ballastlane.assessment.application.port.out;

public interface PasswordResetNotifier {
    /** Delivers the reset link for the raw token. Must not fail the caller: delivery problems are the adapter's to log. */
    void sendResetLink(String email, String rawToken);
}
