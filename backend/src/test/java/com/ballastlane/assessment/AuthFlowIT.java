package com.ballastlane.assessment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Whole stack, no mocks: HTTP -> security -> use case -> JPA -> H2 (same Flyway migration as production). */
@SpringBootTest(properties = "app.seed.demo-user.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIT {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper json;

    private String loginAndGetToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asString();
    }

    @Test
    void registerThenLoginThenAccessProtectedArea() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"misty\",\"email\":\"misty@cerulean.city\",\"password\":\"Starmie2024\"}"))
                .andExpect(status().isCreated());

        String token = loginAndGetToken("misty", "Starmie2024");

        // No protected controller exists yet: an authenticated call gets past security and reaches MVC (404),
        // an anonymous one is stopped by security (401).
        mockMvc.perform(get("/api/v1/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void seededDemoUserCanLogIn() throws Exception {
        loginAndGetToken("demo", "Demo12345");
    }

    @Test
    void duplicateRegistrationReturns409AndWrongPasswordReturns401() throws Exception {
        String body = "{\"username\":\"brock\",\"email\":\"brock@pewter.city\",\"password\":\"Onix12345\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"brock\",\"password\":\"wrong-pass1\"}"))
                .andExpect(status().isUnauthorized());
    }
}
