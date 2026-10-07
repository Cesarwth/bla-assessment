package com.ballastlane.assessment.infrastructure.web.dto;

import com.ballastlane.assessment.application.port.in.UpdatedProfile;
import java.time.Instant;

/** Same fields as the login response plus the updated user, so a client can swap its token and its cached profile at once. */
public record ProfileUpdateResponse(String token, String tokenType, Instant expiresAt, UserResponse user) {

    public static ProfileUpdateResponse from(UpdatedProfile updated) {
        return new ProfileUpdateResponse(
                updated.token().jwt(), "Bearer", updated.token().expiresAt(), UserResponse.from(updated.user()));
    }
}
