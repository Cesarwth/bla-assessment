package com.ballastlane.assessment.application.port.in;

public record ChangePasswordCommand(Long userId, String currentPassword, String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordCommand[userId=" + userId + "]";
    }
}
