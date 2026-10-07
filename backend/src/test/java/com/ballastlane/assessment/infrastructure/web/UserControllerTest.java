package com.ballastlane.assessment.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.application.port.in.ChangePasswordCommand;
import com.ballastlane.assessment.application.port.in.ChangePasswordUseCase;
import com.ballastlane.assessment.application.port.in.GetProfileUseCase;
import com.ballastlane.assessment.application.port.in.UpdateProfileCommand;
import com.ballastlane.assessment.application.port.in.UpdateProfileUseCase;
import com.ballastlane.assessment.application.port.in.UpdatedProfile;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.AuthToken;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.InvalidCurrentPasswordException;
import com.ballastlane.assessment.domain.exception.InvalidUserException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.WeakPasswordException;
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

@WebMvcTest(UserController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    private static final User ASH = new User(1L, "ash", "ash@pallet.town", "hashed", Role.USER);
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mockMvc;
    @Autowired TokenIssuer tokens;
    @MockitoBean GetProfileUseCase getProfile;
    @MockitoBean UpdateProfileUseCase updateProfile;
    @MockitoBean ChangePasswordUseCase changePassword;

    private String bearer() {
        return "Bearer " + tokens.issue(ASH).jwt();
    }

    @Test
    void meReturnsTheUserWithoutSecrets() throws Exception {
        when(getProfile.get(new Actor(1L, "ash", Role.USER))).thenReturn(ASH);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("ash"))
                .andExpect(jsonPath("$.email").value("ash@pallet.town"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void meNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(getProfile);
    }

    @Test
    void patchReturnsTheLoginShapePlusTheUpdatedUser() throws Exception {
        User renamed = new User(1L, "red", "ash@pallet.town", "hashed", Role.USER);
        when(updateProfile.update(any())).thenReturn(
                new UpdatedProfile(renamed, new AuthToken("new-jwt", Instant.parse("2030-01-01T00:00:00Z"))));

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", bearer()).contentType(JSON)
                        .content("{\"username\":\"red\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.username").value("red"))
                .andExpect(jsonPath("$.user.email").value("ash@pallet.town"))
                .andExpect(jsonPath("$.user.role").value("USER"));
        verify(updateProfile).update(new UpdateProfileCommand(1L, "red", null, null));
    }

    @Test
    void patchPassesTheEmailAndThePasswordThrough() throws Exception {
        when(updateProfile.update(any())).thenReturn(
                new UpdatedProfile(ASH, new AuthToken("jwt", Instant.parse("2030-01-01T00:00:00Z"))));

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", bearer()).contentType(JSON)
                        .content("{\"email\":\"new@pallet.town\",\"currentPassword\":\"Pikachu123\"}"))
                .andExpect(status().isOk());
        verify(updateProfile).update(new UpdateProfileCommand(1L, null, "new@pallet.town", "Pikachu123"));
    }

    @Test
    void patchWithNothingToUpdateIs400() throws Exception {
        doThrow(new InvalidUserException("Provide a username or an email to update")).when(updateProfile).update(any());

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", bearer()).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void patchRejectsAMalformedEmailAndAShortUsername() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", bearer()).contentType(JSON)
                        .content("{\"username\":\"ab\",\"email\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
        verifyNoInteractions(updateProfile);
    }

    @Test
    void patchWithAWrongCurrentPasswordIs400NotA401() throws Exception {
        doThrow(new InvalidCurrentPasswordException()).when(updateProfile).update(any());

        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", bearer()).contentType(JSON)
                        .content("{\"email\":\"new@pallet.town\",\"currentPassword\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void patchConflictsAre409() throws Exception {
        doThrow(new UsernameAlreadyExistsException("red")).when(updateProfile).update(any());
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", bearer()).contentType(JSON)
                        .content("{\"username\":\"red\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

        doThrow(new EmailAlreadyExistsException("x")).when(updateProfile).update(any());
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", bearer()).contentType(JSON)
                        .content("{\"email\":\"red@pallet.town\",\"currentPassword\":\"Pikachu123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void changePasswordAnswersNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/password").header("Authorization", bearer()).contentType(JSON)
                        .content("{\"currentPassword\":\"Pikachu123\",\"newPassword\":\"Raichu456\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(changePassword).change(new ChangePasswordCommand(1L, "Pikachu123", "Raichu456"));
    }

    @Test
    void changePasswordWithAWrongCurrentPasswordIs400NotA401() throws Exception {
        doThrow(new InvalidCurrentPasswordException()).when(changePassword).change(any());

        mockMvc.perform(post("/api/v1/users/me/password").header("Authorization", bearer()).contentType(JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"Raichu456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void changePasswordWithAWeakNewPasswordIs400() throws Exception {
        doThrow(new WeakPasswordException("weak")).when(changePassword).change(any());

        mockMvc.perform(post("/api/v1/users/me/password").header("Authorization", bearer()).contentType(JSON)
                        .content("{\"currentPassword\":\"Pikachu123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void changePasswordRequiresBothFields() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/password").header("Authorization", bearer()).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void changePasswordNeedsAToken() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/password").contentType(JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(changePassword);
    }
}
