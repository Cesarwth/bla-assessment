package com.ballastlane.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.support.FakePokeApi;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Whole stack, no mocks: login (demo USER, a second USER and the ADMIN) -> sync from a local PokeAPI stand-in -> read
 * -> patch with versions -> ownership rules -> delete, on the real schema built by the same Flyway migrations as
 * production.
 */
@SpringBootTest(properties = {
        "app.seed.demo-user.enabled=true",
        "app.seed.demo-user.admin-password=AdminPass123",
        "app.pokeapi.read-timeout=500ms"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CollectionFlowIT {

    private static final FakePokeApi UPSTREAM = FakePokeApi.start();
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @DynamicPropertySource
    static void upstreamUrl(DynamicPropertyRegistry registry) {
        registry.add("app.pokeapi.base-url", UPSTREAM::baseUrl);
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void emptyCollection() {
        jdbc.update("delete from pokemon"); // child rows go with it (ON DELETE CASCADE)
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop();
    }

    // ---- helpers ----

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asString();
    }

    private String user() throws Exception {
        return login("demo", "Demo12345");
    }

    private String admin() throws Exception {
        return login("demo-admin", "AdminPass123");
    }

    /** A second regular user, registered on first use (the database lives as long as the Spring context). */
    private String otherUser() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(JSON)
                .content("{\"username\":\"other\",\"email\":\"other@pallet.town\",\"password\":\"Other12345\"}"));
        return login("other", "Other12345");
    }

    /** The uid claim of a token: what the frontend will compare with {@code ownerId}. */
    private long uidOf(String bearer) {
        String payload = bearer.substring("Bearer ".length()).split("\\.")[1];
        return json.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)).get("uid").asLong();
    }

    private long syncAs(String token, String idOrName) throws Exception {
        String body = mvc.perform(post("/api/v1/collection/" + idOrName + "/sync").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("version").asLong();
    }

    private String patchBody(long version, String fields) {
        return "{\"version\":" + version + (fields.isEmpty() ? "" : "," + fields) + "}";
    }

    // ---- flows ----

    @Test
    void syncPatchResyncKeepsLocalEditsRefreshesTheRestAndTracksTheOwner() throws Exception {
        String token = user();

        mvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.ownerId").value(uidOf(token)))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.nickname").isEmpty())
                .andExpect(jsonPath("$.evolutionChain.name").value("pichu"));

        mvc.perform(patch("/api/v1/collection/25").header("Authorization", token).contentType(JSON)
                        .content(patchBody(0, "\"nickname\":\"Pika\",\"region\":\"Kanto\",\"tags\":[\"Starter\",\"ELECTRIC\",\"starter\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.ownerId").value(uidOf(token)))
                .andExpect(jsonPath("$.nickname").value("Pika"))
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.tags[0]").value("starter"))
                .andExpect(jsonPath("$.tags[1]").value("electric"));

        // Same Pokemon requested by id this time: still an update of the same row, local edits intact.
        mvc.perform(post("/api/v1/collection/25/sync").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Pika"))
                .andExpect(jsonPath("$.region").value("Kanto"))
                .andExpect(jsonPath("$.tags[0]").value("starter"))
                .andExpect(jsonPath("$.weightKg").value(6.0));

        assertThat(jdbc.queryForObject("select count(*) from pokemon", Integer.class)).isEqualTo(1);
    }

    @Test
    void anotherUserCannotEditButTheOwnerAndAnAdminCan() throws Exception {
        String owner = user();
        String stranger = otherUser();
        long version = syncAs(owner, "pikachu");

        mvc.perform(patch("/api/v1/collection/25").header("Authorization", stranger).contentType(JSON)
                        .content(patchBody(version, "\"nickname\":\"Stolen\"")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_NOT_OWNER"));
        // ownership is checked before the version: a stranger learns nothing about whether a version is current
        mvc.perform(patch("/api/v1/collection/25").header("Authorization", stranger).contentType(JSON)
                        .content(patchBody(version + 99, "\"nickname\":\"Stolen\"")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_NOT_OWNER"));
        mvc.perform(get("/api/v1/collection/25")).andExpect(jsonPath("$.nickname").isEmpty());

        mvc.perform(patch("/api/v1/collection/25").header("Authorization", admin()).contentType(JSON)
                        .content(patchBody(version, "\"nickname\":\"By admin\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("By admin"))
                .andExpect(jsonPath("$.ownerId").value(uidOf(owner)));
    }

    @Test
    void aResyncByAnotherUserKeepsTheOriginalOwnerAndGrantsNothing() throws Exception {
        String owner = user();
        String stranger = otherUser();
        syncAs(owner, "pikachu");

        mvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(uidOf(owner)));

        mvc.perform(patch("/api/v1/collection/25").header("Authorization", stranger).contentType(JSON)
                        .content(patchBody(1, "\"nickname\":\"x\"")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStaleVersionIsRejectedAndTheFreshOneBumpsTheVersionAgain() throws Exception {
        String token = user();
        long v0 = syncAs(token, "pikachu");

        mvc.perform(patch("/api/v1/collection/25").header("Authorization", token).contentType(JSON)
                        .content(patchBody(v0, "\"nickname\":\"first\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(v0 + 1));
        mvc.perform(patch("/api/v1/collection/25").header("Authorization", token).contentType(JSON)
                        .content(patchBody(v0, "\"nickname\":\"second from a stale screen\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mvc.perform(patch("/api/v1/collection/25").header("Authorization", token).contentType(JSON)
                        .content(patchBody(v0 + 1, "\"nickname\":\"second\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(v0 + 2))
                .andExpect(jsonPath("$.nickname").value("second"));
    }

    @Test
    void patchRulesAreDefensive() throws Exception {
        String token = user();
        long version = syncAs(token, "pikachu");
        String url = "/api/v1/collection/25";

        mvc.perform(patch(url).header("Authorization", token).contentType(JSON).content("{\"nickname\":\"no version\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
        mvc.perform(patch(url).header("Authorization", token).contentType(JSON).content(patchBody(version, "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_UPDATE"));
        mvc.perform(patch(url).header("Authorization", token).contentType(JSON)
                        .content(patchBody(version, "\"nickname\":\"" + "x".repeat(51) + "\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LOCAL_ATTRIBUTES"));
        mvc.perform(patch(url).header("Authorization", token).contentType(JSON).content("{oops"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(patch("/api/v1/collection/999").header("Authorization", token).contentType(JSON)
                        .content(patchBody(0, "\"nickname\":\"x\"")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
        mvc.perform(patch(url).contentType(JSON).content(patchBody(version, "\"nickname\":\"x\"")))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch(url).header("Authorization", token).contentType(JSON)
                        .content(patchBody(version, "\"nickname\":\"\",\"tags\":[]")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").isEmpty()).andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void publicReadsNeedNoTokenAndShowTheOwner() throws Exception {
        String owner = user();
        mvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", owner)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/collection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(25))
                .andExpect(jsonPath("$.items[0].ownerId").value(uidOf(owner)));
        mvc.perform(get("/api/v1/collection/25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats[0].name").value("hp"))
                .andExpect(jsonPath("$.evolutionChain.evolvesTo[0].evolvesTo[0].name").value("raichu"));
        mvc.perform(get("/api/v1/collection/404")).andExpect(status().isNotFound());
    }

    @Test
    void onlyAnAdminCanDelete() throws Exception {
        String owner = user();
        mvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", owner)).andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/collection/25")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/collection/25").header("Authorization", owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/collection/25")).andExpect(status().isOk());

        String admin = admin();
        mvc.perform(delete("/api/v1/collection/25").header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/collection/25")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/collection/25").header("Authorization", admin)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from pokemon_types", Integer.class)).isZero();
    }

    @Test
    void rangeSyncReportsTheIdsMissingUpstreamAsFailuresAndOwnsWhatItCreates() throws Exception {
        String token = user();
        mvc.perform(post("/api/v1/collection/sync").header("Authorization", token).contentType(JSON)
                        .content("{\"from\":1025,\"to\":1027}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.failures", hasSize(2)))
                .andExpect(jsonPath("$.failures[0].identifier").value("1026"))
                .andExpect(jsonPath("$.failures[0].reason").value("Not found upstream"))
                .andExpect(jsonPath("$.abortedReason").isEmpty());
        mvc.perform(get("/api/v1/collection/1025")).andExpect(jsonPath("$.ownerId").value(uidOf(token)));

        mvc.perform(post("/api/v1/collection/sync").header("Authorization", token).contentType(JSON)
                        .content("{\"from\":1,\"to\":21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SYNC_RANGE"));
    }

    @Test
    void anUpstreamOutageDuringSyncIs502AndStoresNothing() throws Exception {
        mvc.perform(post("/api/v1/collection/glitch/sync").header("Authorization", user()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
        mvc.perform(post("/api/v1/collection/missingno/sync").header("Authorization", user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POKEMON_NOT_FOUND"));

        assertThat(jdbc.queryForObject("select count(*) from pokemon", Integer.class)).isZero();
    }

    @Test
    void ofManyEditsFromTheSameVersionExactlyOneWinsAndNothingIsCorrupted() throws Exception {
        String token = user();
        long version = syncAs(token, "pikachu");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> edits = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String body = patchBody(version, "\"nickname\":\"edit-" + i + "\"");
                edits.add(() -> mvc.perform(patch("/api/v1/collection/25").header("Authorization", token)
                        .contentType(JSON).content(body)).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(edits)) {
                statuses.add(result.get());
            }

            // Every edit started from the same version, so optimistic locking lets exactly one through: no 500, no lost update.
            assertThat(statuses).allMatch(s -> s == 200 || s == 409);
            assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select version from pokemon where pokeapi_id = 25", Long.class))
                    .isEqualTo(version + 1);
        } finally {
            pool.shutdownNow();
        }
    }
}
