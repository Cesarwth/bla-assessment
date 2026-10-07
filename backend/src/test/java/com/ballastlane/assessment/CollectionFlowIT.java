package com.ballastlane.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.support.FakePokeApi;
import java.util.ArrayList;
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
 * Whole stack, no mocks: login (demo USER, a second USER and the ADMIN) -> sync from a local PokeAPI stand-in into the
 * trainer's own collection -> read -> patch with versions -> isolation between trainers -> delete, on the real schema
 * built by the same Flyway migrations as production.
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

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void emptyCollection() {
        jdbcTemplate.update("delete from pokemon"); // child rows go with it (ON DELETE CASCADE)
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop();
    }

    // ---- helpers ----

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(JSON)
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
        mockMvc.perform(post("/api/v1/auth/register").contentType(JSON)
                .content("{\"username\":\"other\",\"email\":\"other@pallet.town\",\"password\":\"Other12345\"}"));
        return login("other", "Other12345");
    }

    private long syncAs(String token, String idOrName) throws Exception {
        String body = mockMvc.perform(post("/api/v1/collection/" + idOrName + "/sync").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("version").asLong();
    }

    private String patchBody(long version, String fields) {
        return "{\"version\":" + version + (fields.isEmpty() ? "" : "," + fields) + "}";
    }

    // ---- flows ----

    @Test
    void syncPatchResyncKeepsLocalEditsAndRefreshesTheRest() throws Exception {
        String token = user();

        mockMvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.nickname").isEmpty())
                .andExpect(jsonPath("$.evolutionChain.name").value("pichu"));

        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", token).contentType(JSON)
                        .content(patchBody(0, "\"nickname\":\"Pika\",\"region\":\"Kanto\",\"tags\":[\"Starter\",\"ELECTRIC\",\"starter\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.nickname").value("Pika"))
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.tags[0]").value("starter"))
                .andExpect(jsonPath("$.tags[1]").value("electric"));

        // Same Pokemon requested by id this time: still the same entry, local edits intact.
        mockMvc.perform(post("/api/v1/collection/25/sync").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Pika"))
                .andExpect(jsonPath("$.region").value("Kanto"))
                .andExpect(jsonPath("$.tags[0]").value("starter"))
                .andExpect(jsonPath("$.weightKg").value(6.0));

        assertThat(jdbcTemplate.queryForObject("select count(*) from pokemon", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from trainer_pokemon", Integer.class)).isEqualTo(1);
    }

    @Test
    void anotherTrainerCannotSeeEditOrRemoveAnEntryNotEvenAnAdmin() throws Exception {
        String mine = user();
        String stranger = otherUser();
        long version = syncAs(mine, "pikachu");

        for (String outsider : List.of(stranger, admin())) {
            mockMvc.perform(get("/api/v1/collection/25").header("Authorization", outsider))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
            mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", outsider).contentType(JSON)
                            .content(patchBody(version, "\"nickname\":\"Stolen\"")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
            mockMvc.perform(delete("/api/v1/collection/25").header("Authorization", outsider)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/collection").header("Authorization", outsider))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        }
        mockMvc.perform(get("/api/v1/collection/25").header("Authorization", mine))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").isEmpty());
    }

    @Test
    void twoTrainersShareTheCatalogRowButKeepTheirOwnNotes() throws Exception {
        String mine = user();
        String theirs = otherUser();

        mockMvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", mine)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", theirs)).andExpect(status().isCreated());
        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", mine).contentType(JSON)
                        .content(patchBody(0, "\"nickname\":\"Sparky\",\"tags\":[\"mine\"]")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", theirs).contentType(JSON)
                        .content(patchBody(0, "\"nickname\":\"Zap\",\"region\":\"Johto\"")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/collection/25").header("Authorization", mine))
                .andExpect(jsonPath("$.nickname").value("Sparky")).andExpect(jsonPath("$.region").isEmpty())
                .andExpect(jsonPath("$.tags[0]").value("mine"));
        mockMvc.perform(get("/api/v1/collection/25").header("Authorization", theirs))
                .andExpect(jsonPath("$.nickname").value("Zap")).andExpect(jsonPath("$.region").value("Johto"))
                .andExpect(jsonPath("$.tags", hasSize(0)));
        assertThat(jdbcTemplate.queryForObject("select count(*) from pokemon", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from trainer_pokemon", Integer.class)).isEqualTo(2);
    }

    @Test
    void addingAPokemonTwiceRefreshesItWithoutTouchingTheNotesOrCreatingADuplicate() throws Exception {
        String token = user();
        long version = syncAs(token, "pikachu");
        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", token).contentType(JSON)
                .content(patchBody(version, "\"nickname\":\"Pika\"")));

        mockMvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Pika"))
                .andExpect(jsonPath("$.version").value(version + 1));
        assertThat(jdbcTemplate.queryForObject("select count(*) from trainer_pokemon", Integer.class)).isEqualTo(1);
    }

    @Test
    void aStaleVersionIsRejectedAndTheFreshOneBumpsTheVersionAgain() throws Exception {
        String token = user();
        long versionAfterSync = syncAs(token, "pikachu");

        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", token).contentType(JSON)
                        .content(patchBody(versionAfterSync, "\"nickname\":\"first\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(versionAfterSync + 1));
        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", token).contentType(JSON)
                        .content(patchBody(versionAfterSync, "\"nickname\":\"second from a stale screen\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", token).contentType(JSON)
                        .content(patchBody(versionAfterSync + 1, "\"nickname\":\"second\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(versionAfterSync + 2))
                .andExpect(jsonPath("$.nickname").value("second"));
    }

    @Test
    void twoTrainersEditingTheirOwnNotesOfTheSamePokemonNeverCollideNorTouchTheCatalogRow() throws Exception {
        String mine = user();
        String theirs = otherUser();
        long mineVersion = syncAs(mine, "pikachu");
        long theirVersion = syncAs(theirs, "pikachu");
        Long catalogVersion = jdbcTemplate.queryForObject("select version from pokemon", Long.class);

        int rounds = 5;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> editors = List.of(
                    () -> editRepeatedly(mine, mineVersion, rounds), () -> editRepeatedly(theirs, theirVersion, rounds));
            for (Future<Integer> done : pool.invokeAll(editors)) {
                assertThat(done.get()).isEqualTo(rounds);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("select version from pokemon", Long.class)).isEqualTo(catalogVersion);
        mockMvc.perform(get("/api/v1/collection/25").header("Authorization", mine))
                .andExpect(jsonPath("$.nickname").value("edit-" + rounds)).andExpect(jsonPath("$.version").value(mineVersion + rounds));
        mockMvc.perform(get("/api/v1/collection/25").header("Authorization", theirs))
                .andExpect(jsonPath("$.nickname").value("edit-" + rounds)).andExpect(jsonPath("$.version").value(theirVersion + rounds));
    }

    /** Returns how many consecutive edits were accepted (each one carries the version the previous one returned). */
    private int editRepeatedly(String token, long firstVersion, int rounds) throws Exception {
        int accepted = 0;
        for (int round = 1; round <= rounds; round++) {
            mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", token).contentType(JSON)
                            .content(patchBody(firstVersion + round - 1, "\"nickname\":\"edit-" + round + "\"")))
                    .andExpect(status().isOk());
            accepted++;
        }
        return accepted;
    }

    @Test
    void patchRulesAreDefensive() throws Exception {
        String token = user();
        long version = syncAs(token, "pikachu");
        String url = "/api/v1/collection/25";

        mockMvc.perform(patch(url).header("Authorization", token).contentType(JSON).content("{\"nickname\":\"no version\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
        mockMvc.perform(patch(url).header("Authorization", token).contentType(JSON).content(patchBody(version, "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_UPDATE"));
        mockMvc.perform(patch(url).header("Authorization", token).contentType(JSON)
                        .content(patchBody(version, "\"nickname\":\"" + "x".repeat(51) + "\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LOCAL_ATTRIBUTES"));
        mockMvc.perform(patch(url).header("Authorization", token).contentType(JSON).content("{oops"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(patch("/api/v1/collection/999").header("Authorization", token).contentType(JSON)
                        .content(patchBody(0, "\"nickname\":\"x\"")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
        mockMvc.perform(patch(url).contentType(JSON).content(patchBody(version, "\"nickname\":\"x\"")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch(url).header("Authorization", token).contentType(JSON)
                        .content(patchBody(version, "\"nickname\":\"\",\"tags\":[]")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").isEmpty()).andExpect(jsonPath("$.tags", hasSize(0)));
    }

    @Test
    void theCollectionRoutesNeedATokenButTheCatalogDoesNot() throws Exception {
        String mine = user();
        mockMvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", mine)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/collection")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/collection/25")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/pokemon/25")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/collection").header("Authorization", mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(25))
                .andExpect(jsonPath("$.items[0].imageUrl").value(org.hamcrest.Matchers.endsWith("/official-artwork/25.png")))
                .andExpect(jsonPath("$.items[0].ownerId").doesNotExist());
        mockMvc.perform(get("/api/v1/collection/25").header("Authorization", mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats[0].name").value("hp"))
                .andExpect(jsonPath("$.evolutionChain.evolvesTo[0].evolvesTo[0].name").value("raichu"));
        mockMvc.perform(get("/api/v1/collection/404").header("Authorization", mine)).andExpect(status().isNotFound());
    }

    @Test
    void idsListsOnlyTheCallersPokemonSortedAndIsEmptyForAnotherTrainer() throws Exception {
        String mine = user();
        syncAs(mine, "pikachu");
        syncAs(mine, "1");

        mockMvc.perform(get("/api/v1/collection/ids")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/collection/ids").header("Authorization", mine))
                .andExpect(status().isOk()).andExpect(content().json("[1,25]", true));
        mockMvc.perform(get("/api/v1/collection/ids").header("Authorization", otherUser()))
                .andExpect(status().isOk()).andExpect(content().json("[]", true));
    }

    @Test
    void anyTrainerRemovesTheirOwnEntryAndTheSharedPokemonStaysForTheOthers() throws Exception {
        String mine = user();
        String otherToken = otherUser();
        syncAs(mine, "pikachu");
        syncAs(otherToken, "pikachu");

        mockMvc.perform(delete("/api/v1/collection/25")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/collection/25").header("Authorization", mine)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/collection/25").header("Authorization", mine)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/collection/25").header("Authorization", mine)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/collection/25").header("Authorization", otherToken)).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select count(*) from pokemon", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from trainer_pokemon", Integer.class)).isEqualTo(1);
    }

    @Test
    void selectionSyncReportsTheIdsMissingUpstreamAsFailuresAndAddsWhatItCreatesToTheCallersCollection() throws Exception {
        String token = user();
        mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", token).contentType(JSON)
                        .content("{\"ids\":[1025,1026,1027,1025]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.failures", hasSize(2)))
                .andExpect(jsonPath("$.failures[0].identifier").value("1026"))
                .andExpect(jsonPath("$.failures[0].reason").value("Not found upstream"))
                .andExpect(jsonPath("$.abortedReason").isEmpty());
        mockMvc.perform(get("/api/v1/collection/1025").header("Authorization", token)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", token).contentType(JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void anUpstreamOutageDuringSyncIs502AndStoresNothing() throws Exception {
        mockMvc.perform(post("/api/v1/collection/glitch/sync").header("Authorization", user()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
        mockMvc.perform(post("/api/v1/collection/missingno/sync").header("Authorization", user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POKEMON_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from pokemon", Integer.class)).isZero();
    }

    @Test
    void ofManyEditsFromTheSameVersionExactlyOneWinsAndNothingIsCorrupted() throws Exception {
        String token = user();
        long version = syncAs(token, "pikachu");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> edits = new ArrayList<>();
            for (int editNumber = 0; editNumber < threads; editNumber++) {
                String body = patchBody(version, "\"nickname\":\"edit-" + editNumber + "\"");
                edits.add(() -> mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", token)
                        .contentType(JSON).content(body)).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> statusFuture : pool.invokeAll(edits)) {
                statuses.add(statusFuture.get());
            }

            // Every edit started from the same version, so optimistic locking lets exactly one through: no 500, no lost update.
            assertThat(statuses).allMatch(status -> status == 200 || status == 409);
            assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("select version from trainer_pokemon", Long.class))
                    .isEqualTo(version + 1);
        } finally {
            pool.shutdownNow();
        }
    }
}
