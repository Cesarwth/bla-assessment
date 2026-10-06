package com.ballastlane.assessment.application.port.in;

public record RegisterUserCommand(String username, String email, String rawPassword) {
}
