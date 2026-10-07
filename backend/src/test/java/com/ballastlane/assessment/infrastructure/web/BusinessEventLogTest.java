package com.ballastlane.assessment.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.ballastlane.assessment.application.port.in.AuthenticateUserUseCase;
import com.ballastlane.assessment.application.port.in.ChangePasswordUseCase;
import com.ballastlane.assessment.application.port.in.DeleteStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.GetProfileUseCase;
import com.ballastlane.assessment.application.port.in.GetStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonIdsUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.application.port.in.RequestPasswordResetUseCase;
import com.ballastlane.assessment.application.port.in.ResetPasswordUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.application.port.in.SyncSelectedPokemonUseCase;
import com.ballastlane.assessment.application.port.in.UpdateProfileUseCase;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.UpdatedProfile;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.domain.AuthToken;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.SyncOutcome;
import com.ballastlane.assessment.domain.SyncReport;
import com.ballastlane.assessment.domain.SyncResult;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.infrastructure.security.JwtConfig;
import com.ballastlane.assessment.infrastructure.security.SecurityConfig;
import com.ballastlane.assessment.support.LogCapture;
import com.ballastlane.assessment.support.TestData;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** What the controllers announce at INFO: ids and field names only, never the e-mail, password, token or username. */
@WebMvcTest({AuthController.class, UserController.class, CollectionController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class})
class BusinessEventLogTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final User ASH = new User(7L, "ash", "ash@pallet.town", "hashed", Role.USER);

    @Autowired MockMvc mockMvc;
    @Autowired TokenIssuer tokens;
    @MockitoBean RegisterUserUseCase registerUser;
    @MockitoBean AuthenticateUserUseCase authenticateUser;
    @MockitoBean RequestPasswordResetUseCase requestPasswordReset;
    @MockitoBean ResetPasswordUseCase resetPassword;
    @MockitoBean GetProfileUseCase getProfile;
    @MockitoBean UpdateProfileUseCase updateProfile;
    @MockitoBean ChangePasswordUseCase changePassword;
    @MockitoBean ListStoredPokemonUseCase listStored;
    @MockitoBean ListStoredPokemonIdsUseCase listIds;
    @MockitoBean GetStoredPokemonUseCase getStored;
    @MockitoBean SyncPokemonUseCase syncOne;
    @MockitoBean SyncSelectedPokemonUseCase syncSelected;
    @MockitoBean UpdateStoredPokemonUseCase updateStored;
    @MockitoBean DeleteStoredPokemonUseCase deleteStored;

    private String bearer() {
        return "Bearer " + tokens.issue(ASH).jwt();
    }

    private static void assertSingleInfo(LogCapture logs, String message) {
        assertThat(logs.events()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo(message);
        });
    }

    @Test
    void registeringLogsTheNewUserId() throws Exception {
        when(registerUser.register(any())).thenReturn(ASH);
        try (LogCapture logs = LogCapture.of(AuthController.class)) {
            mockMvc.perform(post("/api/v1/auth/register").contentType(JSON)
                    .content("{\"username\":\"ash\",\"email\":\"ash@pallet.town\",\"password\":\"Pikachu123\"}"));

            assertSingleInfo(logs, "User 7 registered");
        }
    }

    @Test
    void requestingAResetLogsNothingAboutWhoOrWhetherTheyExist() throws Exception {
        try (LogCapture logs = LogCapture.of(AuthController.class)) {
            mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(JSON).content("{\"email\":\"ash@pallet.town\"}"));

            assertSingleInfo(logs, "Password reset requested");
        }
    }

    @Test
    void completingAResetIsLogged() throws Exception {
        try (LogCapture logs = LogCapture.of(AuthController.class)) {
            mockMvc.perform(post("/api/v1/auth/reset-password").contentType(JSON)
                    .content("{\"token\":\"raw-token\",\"newPassword\":\"Pikachu123\"}"));

            assertSingleInfo(logs, "Password reset completed");
        }
    }

    @Test
    void changingThePasswordLogsTheUserId() throws Exception {
        try (LogCapture logs = LogCapture.of(UserController.class)) {
            mockMvc.perform(post("/api/v1/users/me/password").header("Authorization", bearer()).contentType(JSON)
                    .content("{\"currentPassword\":\"Pikachu123\",\"newPassword\":\"Raichu12345\"}"));

            assertSingleInfo(logs, "User 7 changed their password");
        }
    }

    @Test
    void updatingTheProfileLogsWhichFieldsWereSentAndNotTheirValues() throws Exception {
        when(updateProfile.update(any())).thenReturn(
                new UpdatedProfile(ASH, new AuthToken("new-jwt", Instant.parse("2030-01-01T00:00:00Z"))));
        try (LogCapture logs = LogCapture.of(UserController.class)) {
            mockMvc.perform(patch("/api/v1/users/me").header("Authorization", bearer()).contentType(JSON)
                    .content("{\"username\":\"red\",\"email\":\"red@pallet.town\",\"currentPassword\":\"Pikachu123\"}"));

            assertSingleInfo(logs, "User 7 updated their profile (fields: username, email)");
        }
    }

    @Test
    void addingAPokemonLogsTheTrainerAndThePokemonButARefreshIsNotAnAddition() throws Exception {
        StoredPokemon stored = TestData.stored(7, 7, 25, "pikachu", new LocalAttributes(null, null, List.of()));
        when(syncOne.sync(eq("pikachu"), any())).thenReturn(new SyncResult(SyncOutcome.CREATED, stored));
        when(syncOne.sync(eq("raichu"), any())).thenReturn(new SyncResult(SyncOutcome.UPDATED, stored));
        try (LogCapture logs = LogCapture.of(CollectionController.class)) {
            mockMvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", bearer()));
            mockMvc.perform(post("/api/v1/collection/raichu/sync").header("Authorization", bearer()));

            assertSingleInfo(logs, "Trainer 7 added Pokemon 25");
        }
    }

    @Test
    void removingAPokemonLogsTheTrainerAndThePokemon() throws Exception {
        try (LogCapture logs = LogCapture.of(CollectionController.class)) {
            mockMvc.perform(delete("/api/v1/collection/25").header("Authorization", bearer()));

            assertSingleInfo(logs, "Trainer 7 removed Pokemon 25");
        }
    }

    @Test
    void aBulkAddLogsTheCountsAndWhyItWasCutShort() throws Exception {
        when(syncSelected.sync(eq(List.of(1, 2, 3)), any())).thenReturn(
                new SyncReport(1, 1, List.of(new SyncReport.Failure("3", "Not found upstream")), "Catalog unavailable at id 3"));
        try (LogCapture logs = LogCapture.of(CollectionController.class)) {
            mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer()).contentType(JSON)
                    .content("{\"ids\":[1,2,3]}"));

            assertSingleInfo(logs, "Trainer 7 bulk add: requested=3 created=1 updated=1 failed=1 aborted=Catalog unavailable at id 3");
        }
    }

    @Test
    void aCompleteBulkAddSaysNoneWasAborted() throws Exception {
        when(syncSelected.sync(eq(List.of(1)), any())).thenReturn(new SyncReport(1, 0, List.of()));
        try (LogCapture logs = LogCapture.of(CollectionController.class)) {
            mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer()).contentType(JSON)
                    .content("{\"ids\":[1]}"));

            assertThat(logs.events()).extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly("Trainer 7 bulk add: requested=1 created=1 updated=0 failed=0 aborted=no");
        }
    }
}
