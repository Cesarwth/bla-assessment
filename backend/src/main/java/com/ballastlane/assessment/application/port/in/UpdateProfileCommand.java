package com.ballastlane.assessment.application.port.in;

/** Null username/email mean "leave as is"; the current password is only needed to change the e-mail. */
public record UpdateProfileCommand(Long userId, String username, String email, String currentPassword) {

    @Override
    public String toString() {
        return "UpdateProfileCommand[userId=" + userId + "]";
    }
}
