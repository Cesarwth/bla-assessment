package com.ballastlane.assessment.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.application.port.in.AuthenticateUserUseCase;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.application.port.in.RequestPasswordResetUseCase;
import com.ballastlane.assessment.application.port.in.ResetPasswordCommand;
import com.ballastlane.assessment.application.port.in.ResetPasswordUseCase;
import com.ballastlane.assessment.domain.AuthToken;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.InvalidCredentialsException;
import com.ballastlane.assessment.domain.exception.InvalidResetTokenException;
import com.ballastlane.assessment.domain.exception.WeakPasswordException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;
import com.ballastlane.assessment.infrastructure.security.JwtConfig;
import com.ballastlane.assessment.infrastructure.security.SecurityConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RegisterUserUseCase registerUser;
    @MockitoBean AuthenticateUserUseCase authenticateUser;
    @MockitoBean RequestPasswordResetUseCase requestPasswordReset;
    @MockitoBean ResetPasswordUseCase resetPassword;

    private static final String VALID_REGISTER =
            "{\"username\":\"ash\",\"email\":\"ash@pallet.town\",\"password\":\"Pikachu123\"}";

    @Test
    void register201ReturnsTheUserWithoutSecrets() throws Exception {
        when(registerUser.register(any())).thenReturn(new User(1L, "ash", "ash@pallet.town", "hashed", Role.USER));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("ash"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register400OnInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"email\":\"nope\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void register409OnDuplicateUsername() throws Exception {
        when(registerUser.register(any())).thenThrow(new UsernameAlreadyExistsException("ash"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"))
                .andExpect(jsonPath("$.message").value("Username is already taken"));
    }

    @Test
    void login200ReturnsABearerToken() throws Exception {
        when(authenticateUser.authenticate(any())).thenReturn(new AuthToken("jwt-value", Instant.parse("2030-01-01T00:00:00Z")));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ash\",\"password\":\"Pikachu123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-01T00:00:00Z"));
    }

    @Test
    void login401OnBadCredentials() throws Exception {
        when(authenticateUser.authenticate(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ash\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login400WhenBodyMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPassword202WithAnEmptyBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ash@pallet.town\"}"))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
        verify(requestPasswordReset).request("ash@pallet.town");
    }

    @Test
    void forgotPasswordAnswersTheSameForAnUnknownEmail() throws Exception {
        // The use case returns normally for unknown accounts, so the controller has nothing to branch on.
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@nowhere.io\"}"))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }

    @Test
    void forgotPassword400OnMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void resetPasswordAnswersNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"newPassword\":\"Raichu456\"}"))
                .andExpect(status().isNoContent());
        verify(resetPassword).reset(new ResetPasswordCommand("abc", "Raichu456"));
    }

    @Test
    void resetPassword400OnAnInvalidToken() throws Exception {
        doThrow(new InvalidResetTokenException()).when(resetPassword).reset(any());

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"newPassword\":\"Raichu456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"))
                .andExpect(jsonPath("$.message").value("This reset link is invalid or has expired"));
    }

    @Test
    void resetPassword400OnAWeakPassword() throws Exception {
        doThrow(new WeakPasswordException("weak")).when(resetPassword).reset(any());

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void resetPassword400OnAMissingField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\",\"newPassword\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }
}
