package com.ballastlane.assessment.application.port.in;

public record ResetPasswordCommand(String token, String newPassword) {

    // The generated toString would print the secrets into logs.
    @Override
    public String toString() {
        return "ResetPasswordCommand[redacted]";
    }
}
