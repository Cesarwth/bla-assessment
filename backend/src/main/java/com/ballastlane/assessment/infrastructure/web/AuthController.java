package com.ballastlane.assessment.infrastructure.web;

import com.ballastlane.assessment.application.port.in.AuthenticateCommand;
import com.ballastlane.assessment.application.port.in.AuthenticateUserUseCase;
import com.ballastlane.assessment.application.port.in.RegisterUserCommand;
import com.ballastlane.assessment.application.port.in.RequestPasswordResetUseCase;
import com.ballastlane.assessment.application.port.in.ResetPasswordCommand;
import com.ballastlane.assessment.application.port.in.ResetPasswordUseCase;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.domain.AuthToken;
import com.ballastlane.assessment.infrastructure.web.dto.AuthResponse;
import com.ballastlane.assessment.infrastructure.web.dto.ForgotPasswordRequest;
import com.ballastlane.assessment.infrastructure.web.dto.LoginRequest;
import com.ballastlane.assessment.infrastructure.web.dto.RegisterRequest;
import com.ballastlane.assessment.infrastructure.web.dto.ResetPasswordRequest;
import com.ballastlane.assessment.infrastructure.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticateUser;

    private final RequestPasswordResetUseCase requestPasswordReset;
    private final ResetPasswordUseCase resetPassword;

    public AuthController(RegisterUserUseCase registerUser, AuthenticateUserUseCase authenticateUser,
            RequestPasswordResetUseCase requestPasswordReset, ResetPasswordUseCase resetPassword) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.requestPasswordReset = requestPasswordReset;
        this.resetPassword = resetPassword;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        UserResponse registered = UserResponse.from(registerUser.register(
                new RegisterUserCommand(request.username(), request.email(), request.password())));
        log.info("User {} registered", registered.id());
        return registered;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthToken token = authenticateUser.authenticate(new AuthenticateCommand(request.username(), request.password()));
        return new AuthResponse(token.jwt(), "Bearer", token.expiresAt());
    }

    // 202 with no body whether or not the account exists: status, body and message must not reveal it.
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        requestPasswordReset.request(request.email());
        // Same line whether or not the account exists, and no e-mail: the log must not reveal what the response hides.
        log.info("Password reset requested");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPassword.reset(new ResetPasswordCommand(request.token(), request.newPassword()));
        // The use case does not report which account the token belonged to, and the token itself must never be logged.
        log.info("Password reset completed");
    }
}
