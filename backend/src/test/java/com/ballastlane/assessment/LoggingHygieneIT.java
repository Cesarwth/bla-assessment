package com.ballastlane.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.ballastlane.assessment.application.port.out.PasswordResetNotifier;
import com.ballastlane.assessment.support.LogCapture;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Runs the account flows with every logger captured and proves no secret or personal datum reaches the logs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoggingHygieneIT {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "Sneasel-Pass2024";
    private static final String NEW_PASSWORD = "Weavile-Pass2024";
    private static final String WRONG_PASSWORD = "Wrong-Pass-2024";
    private static final String EMAIL = "hygiene@sinnoh.region";
    private static final String NEW_EMAIL = "changed@sinnoh.region";

    static class CapturingNotifier implements PasswordResetNotifier {
        final List<String> tokens = new ArrayList<>();

        @Override
        public void sendResetLink(String email, String rawToken) {
            tokens.add(rawToken);
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

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper json;
    @Autowired CapturingNotifier mail;

    private String send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int expected)
            throws Exception {
        return mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void noSecretOrPersonalDataIsLoggedAcrossRegisterLoginResetAndProfileUpdate() throws Exception {
        // INFO is the shipped default; Spring Security's own DEBUG output prints the bearer token, which is why operators
        // must not run the root logger at DEBUG in production (documented in the README).
        try (LogCapture logs = LogCapture.ofRoot().at(Level.INFO)) {
            send(post("/api/v1/auth/register").contentType(JSON)
                    .content("{\"username\":\"hygiene\",\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"), 201);
            send(post("/api/v1/auth/login").contentType(JSON)
                    .content("{\"username\":\"hygiene\",\"password\":\"" + WRONG_PASSWORD + "\"}"), 401);
            send(post("/api/v1/auth/forgot-password").contentType(JSON).content("{\"email\":\"" + EMAIL + "\"}"), 202);
            String resetToken = mail.tokens.get(mail.tokens.size() - 1);
            send(post("/api/v1/auth/reset-password").contentType(JSON)
                    .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"), 204);
            String loginBody = send(post("/api/v1/auth/login").contentType(JSON)
                    .content("{\"username\":\"hygiene\",\"password\":\"" + NEW_PASSWORD + "\"}"), 200);
            String jwt = json.readTree(loginBody).get("token").asString();
            send(patch("/api/v1/users/me").header("Authorization", "Bearer " + jwt).contentType(JSON)
                    .content("{\"email\":\"" + NEW_EMAIL + "\",\"currentPassword\":\"" + NEW_PASSWORD + "\"}"), 200);

            assertThat(logs.events()).isNotEmpty();
            String everything = String.join("\n", logs.events().stream().map(LoggingHygieneIT::render).toList());
            assertThat(everything)
                    .contains("Login failed", "Password reset requested", "Password reset completed", "registered")
                    .doesNotContain(PASSWORD, NEW_PASSWORD, WRONG_PASSWORD, resetToken, EMAIL, NEW_EMAIL, jwt);
        }
    }

    private static String render(ILoggingEvent event) {
        return event.getFormattedMessage() + " " + event.getMDCPropertyMap()
                + (event.getThrowableProxy() == null ? "" : " " + event.getThrowableProxy().getMessage());
    }
}
