package com.ballastlane.assessment.application.port.in;

public interface RequestPasswordResetUseCase {
    /** Never reveals whether the e-mail belongs to an account: it returns normally either way. */
    void request(String email);
}
