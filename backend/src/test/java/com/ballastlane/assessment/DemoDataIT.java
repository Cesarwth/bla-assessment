package com.ballastlane.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.infrastructure.config.DemoDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** The system as a stranger finds it after `docker compose up`: accounts and a small collection already there. */
@SpringBootTest(properties = {
        "app.seed.demo-user.enabled=true",
        "app.seed.demo-user.admin-password=AdminPass123",
        "app.seed.demo-data.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoDataIT {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoDataSeeder seeder;

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asString();
    }

    private long versionOf(int pokeApiId) throws Exception {
        String body = mvc.perform(get("/api/v1/collection/" + pokeApiId)).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("version").asLong();
    }

    private long idOfUser(String username) {
        return jdbc.queryForObject("select id from users where username = ?", Long.class, username);
    }

    @Test
    void theSystemStartsWithBothAccountsAndTheDemoCollection() throws Exception {
        login("demo", "Demo12345");
        login("demo-admin", "AdminPass123");

        mvc.perform(get("/api/v1/collection").param("size", "48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(16))
                .andExpect(jsonPath("$.items[*].name").value(hasItems("pikachu", "eevee", "mewtwo", "bulbasaur")));
    }

    @Test
    void entriesCarryTheirNicknamesAndTheirOwners() throws Exception {
        mvc.perform(get("/api/v1/collection/25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Sparky"))
                .andExpect(jsonPath("$.tags[0]").value("favorite"))
                .andExpect(jsonPath("$.ownerId").value(idOfUser("demo")))
                .andExpect(jsonPath("$.evolutionChain.name").value("pichu"));
        mvc.perform(get("/api/v1/collection/26"))
                .andExpect(jsonPath("$.ownerId").value(idOfUser("demo-admin")));
    }

    @Test
    void theDemoUserEditsItsOwnPokemonButIsRefusedOnTheAdminOnes() throws Exception {
        String demo = login("demo", "Demo12345");

        mvc.perform(patch("/api/v1/collection/4").header("Authorization", demo).contentType(JSON)
                        .content("{\"version\":" + versionOf(4) + ",\"nickname\":\"Charmy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Charmy"));
        mvc.perform(patch("/api/v1/collection/26").header("Authorization", demo).contentType(JSON)
                        .content("{\"version\":" + versionOf(26) + ",\"nickname\":\"Mine\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_NOT_OWNER"));
    }

    @Test
    void theAdminEditsAnything() throws Exception {
        String admin = login("demo-admin", "AdminPass123");

        mvc.perform(patch("/api/v1/collection/7").header("Authorization", admin).contentType(JSON)
                        .content("{\"version\":" + versionOf(7) + ",\"region\":\"Cerulean City\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("Cerulean City"));
    }

    @Test
    void runningTheSeederAgainNeitherDuplicatesNorUndoesEdits() throws Exception {
        String demo = login("demo", "Demo12345");
        mvc.perform(patch("/api/v1/collection/133").header("Authorization", demo).contentType(JSON)
                        .content("{\"version\":" + versionOf(133) + ",\"nickname\":\"Edited by a user\"}"))
                .andExpect(status().isOk());

        seeder.run(null);

        assertThat(jdbc.queryForObject("select count(*) from pokemon", Integer.class)).isEqualTo(16);
        mvc.perform(get("/api/v1/collection/133")).andExpect(jsonPath("$.nickname").value("Edited by a user"));
    }
}
