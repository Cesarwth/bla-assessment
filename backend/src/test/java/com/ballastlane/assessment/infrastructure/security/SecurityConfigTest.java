package com.ballastlane.assessment.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigTest.ProbeController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {

    private static final User ASH = new User(1L, "ash", "ash@pallet.town", "hashed", Role.USER);

    @Autowired MockMvc mockMvc;
    @Autowired TokenIssuer tokenIssuer;
    @Autowired JwtEncoder encoder;

    @Test
    void publicRoutesAreOpen() throws Exception {
        mockMvc.perform(get("/api/v1/pokemon")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    @Test
    void protectedRouteReturns401WithConsistentBodyWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedRouteReturns401WithGarbageToken() throws Exception {
        mockMvc.perform(get("/api/v1/protected").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedRouteReturns401WithExpiredToken() throws Exception {
        TokenIssuer old = new JwtTokenIssuer(encoder, Duration.ofMinutes(1),
                Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC));

        mockMvc.perform(get("/api/v1/protected").header("Authorization", "Bearer " + old.issue(ASH).jwt()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteReturns200WithValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/protected").header("Authorization", "Bearer " + tokenIssuer.issue(ASH).jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string("ash"));
    }

    @Test
    void corsPreflightAllowsOnlyTheConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/auth/login")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/pokemon") String publicRoute() { return "open"; }
        @GetMapping("/api/v1/protected") String protectedRoute(java.security.Principal principal) { return principal.getName(); }
    }
}
