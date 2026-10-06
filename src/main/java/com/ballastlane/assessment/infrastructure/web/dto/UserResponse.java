package com.ballastlane.assessment.infrastructure.web.dto;

import com.ballastlane.assessment.domain.User;

public record UserResponse(Long id, String username, String email, String role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.username(), user.email(), user.role().name());
    }
}
