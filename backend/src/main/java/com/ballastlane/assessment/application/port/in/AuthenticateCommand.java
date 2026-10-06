package com.ballastlane.assessment.application.port.in;

public record AuthenticateCommand(String username, String rawPassword) {
}
