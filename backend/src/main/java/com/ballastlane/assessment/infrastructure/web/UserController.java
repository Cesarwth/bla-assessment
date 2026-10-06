package com.ballastlane.assessment.infrastructure.web;

import com.ballastlane.assessment.application.port.in.ChangePasswordCommand;
import com.ballastlane.assessment.application.port.in.ChangePasswordUseCase;
import com.ballastlane.assessment.application.port.in.GetProfileUseCase;
import com.ballastlane.assessment.application.port.in.UpdateProfileCommand;
import com.ballastlane.assessment.application.port.in.UpdateProfileUseCase;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.infrastructure.web.dto.ChangePasswordRequest;
import com.ballastlane.assessment.infrastructure.web.dto.ProfileUpdateResponse;
import com.ballastlane.assessment.infrastructure.web.dto.UpdateProfileRequest;
import com.ballastlane.assessment.infrastructure.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated user's own account. There is no user id in any path: it always means "whoever the token says". */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final GetProfileUseCase getProfile;
    private final UpdateProfileUseCase updateProfile;
    private final ChangePasswordUseCase changePassword;

    public UserController(
            GetProfileUseCase getProfile, UpdateProfileUseCase updateProfile, ChangePasswordUseCase changePassword) {
        this.getProfile = getProfile;
        this.updateProfile = updateProfile;
        this.changePassword = changePassword;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(getProfile.get(ActorResolver.from(jwt)));
    }

    // SHORTCUT: tokens are stateless, so the token that carried the old username stays valid until it expires.
    // A denylist or a token version on the user row would close that; not worth the weight for this scope.
    @PatchMapping
    public ProfileUpdateResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        Actor actor = ActorResolver.from(jwt);
        return ProfileUpdateResponse.from(updateProfile.update(
                new UpdateProfileCommand(actor.userId(), request.username(), request.email(), request.currentPassword())));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        changePassword.change(new ChangePasswordCommand(
                ActorResolver.from(jwt).userId(), request.currentPassword(), request.newPassword()));
    }
}
