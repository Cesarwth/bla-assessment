package com.ballastlane.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.application.port.out.PasswordResetNotifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Whole stack with the mail adapter swapped for a fake that remembers what it was asked to send. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordRecoveryFlowIT {

    record Sent(String email, String token) {
    }

    static class CapturingNotifier implements PasswordResetNotifier {
        final List<Sent> sent = new ArrayList<>();

        @Override
        public void sendResetLink(String email, String rawToken) {
            sent.add(new Sent(email, rawToken));
        }
    }

    @TestConfiguration
    static class FakeMail {
        @Bean
        @Primary
        CapturingNotifier capturingNotifier() {
            return new CapturingNotifier();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired CapturingNotifier mail;
    @Autowired JwtDecoder jwtDecoder;

    private void register(String username, String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password, int expectedStatus) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return expectedStatus == 200 ? json.readTree(body).get("token").asString() : null;
    }

    private void forgot(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
    }

    private int reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void forgotThenResetChangesThePasswordAndTheLinkWorksOnce() throws Exception {
        register("dawn", "dawn@twinleaf.town", "Piplup2024");
        mail.sent.clear();

        forgot("DAWN@twinleaf.town");
        assertThat(mail.sent).hasSize(1);
        String token = mail.sent.get(0).token();
        assertThat(mail.sent.get(0).email()).isEqualTo("dawn@twinleaf.town");

        assertThat(reset(token, "Empoleon99")).isEqualTo(204);

        login("dawn", "Piplup2024", 401);
        login("dawn", "Empoleon99", 200);
        assertThat(reset(token, "Another123")).isEqualTo(400); // single use
        login("dawn", "Empoleon99", 200);
    }

    @Test
    void anUnknownEmailGetsTheSame202AndNothingIsSent() throws Exception {
        mail.sent.clear();

        forgot("ghost@nowhere.io");

        assertThat(mail.sent).isEmpty();
    }

    @Test
    void aNewLinkInvalidatesTheOlderOne() throws Exception {
        register("lucas", "lucas@twinleaf.town", "Turtwig2024");
        mail.sent.clear();

        forgot("lucas@twinleaf.town");
        forgot("lucas@twinleaf.town");

        assertThat(mail.sent).hasSize(2);
        assertThat(reset(mail.sent.get(0).token(), "Torterra99")).isEqualTo(400);
        assertThat(reset(mail.sent.get(1).token(), "Torterra99")).isEqualTo(204);
    }

    @Test
    void anInvalidTokenAndAWeakPasswordUseTheirOwnCodes() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"nope\",\"newPassword\":\"Torterra99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"))
                .andExpect(jsonPath("$.message").value("This reset link is invalid or has expired"));
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"nope\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void profileUpdateRenamesTheAccountAndReturnsATokenForTheNewName() throws Exception {
        register("barry", "barry@twinleaf.town", "Infernape2024");
        String oldToken = login("barry", "Infernape2024", 200);

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("barry"));

        String body = mvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"  Barry-Jr \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("barry-jr"))
                .andExpect(jsonPath("$.user.email").value("barry@twinleaf.town"))
                .andReturn().getResponse().getContentAsString();
        String newToken = json.readTree(body).get("token").asString();

        assertThat(jwtDecoder.decode(newToken).getSubject()).isEqualTo("barry-jr");
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + newToken))
                .andExpect(jsonPath("$.username").value("barry-jr"));
        login("barry-jr", "Infernape2024", 200);
        login("barry", "Infernape2024", 401);
    }

    @Test
    void changingTheEmailNeedsTheCurrentPasswordAndTakenValuesConflict() throws Exception {
        register("cynthia", "cynthia@sinnoh.region", "Garchomp2024");
        register("cyrus", "cyrus@sinnoh.region", "Palkia2024");
        String token = login("cynthia", "Garchomp2024", 200);

        for (String payload : List.of("{\"email\":\"new@sinnoh.region\"}",
                "{\"email\":\"new@sinnoh.region\",\"currentPassword\":\"wrong-pass1\"}")) {
            mvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        }
        mvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cyrus@sinnoh.region\",\"currentPassword\":\"Garchomp2024\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        mvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"cyrus\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"champion@sinnoh.region\",\"currentPassword\":\"Garchomp2024\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("champion@sinnoh.region"));

        // resets now go to the new address
        mail.sent.clear();
        forgot("champion@sinnoh.region");
        assertThat(mail.sent).hasSize(1);
    }

    @Test
    void changePasswordSwitchesWhichPasswordLogsIn() throws Exception {
        register("serena", "serena@kalos.region", "Braixen2024");
        String token = login("serena", "Braixen2024", 200);

        mvc.perform(post("/api/v1/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-pass1\",\"newPassword\":\"Delphox2024\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        mvc.perform(post("/api/v1/users/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Braixen2024\",\"newPassword\":\"Delphox2024\"}"))
                .andExpect(status().isNoContent());

        login("serena", "Braixen2024", 401);
        login("serena", "Delphox2024", 200);
    }

    @Test
    void theUsersRoutesRejectAnonymousCallers() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
}
