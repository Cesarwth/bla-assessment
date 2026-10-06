package com.ballastlane.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Whole stack against a local stand-in for PokeAPI serving the captured fixtures:
 * controller -> use case -> gateway -> HTTP client (real timeouts) -> cache. No mocks, no public network.
 */
@SpringBootTest(properties = "app.pokeapi.read-timeout=500ms")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PokemonFlowIT {

    private static final HttpServer UPSTREAM = startUpstream();
    private static final Map<String, AtomicInteger> HITS = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void upstreamUrl(DynamicPropertyRegistry registry) {
        registry.add("app.pokeapi.base-url", () -> "http://localhost:" + UPSTREAM.getAddress().getPort() + "/api/v2");
    }

    @Autowired MockMvc mvc;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void resetCachesAndCounters() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        HITS.clear();
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @Test
    void listsAPageThenOpensADetailWithItsEvolutionLine() throws Exception {
        mvc.perform(get("/api/v1/pokemon").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[2].id").value(3))
                .andExpect(jsonPath("$.items[0].types[0]").value("electric"))
                .andExpect(jsonPath("$.totalItems").value(1351))
                .andExpect(jsonPath("$.totalPages").value(451));

        mvc.perform(get("/api/v1/pokemon/Pikachu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("pikachu"))
                .andExpect(jsonPath("$.weightKg").value(6.0))
                .andExpect(jsonPath("$.genus").value("Mouse Pokémon"))
                .andExpect(jsonPath("$.description").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\n"))))
                .andExpect(jsonPath("$.evolutionChain.name").value("pichu"))
                .andExpect(jsonPath("$.evolutionChain.evolvesTo[0].evolvesTo[0].name").value("raichu"));
    }

    @Test
    void aRepeatedPageIsServedFromTheCacheWithoutCallingUpstreamAgain() throws Exception {
        mvc.perform(get("/api/v1/pokemon").param("size", "3")).andExpect(status().isOk());
        int upstreamCallsAfterFirst = totalHits();

        mvc.perform(get("/api/v1/pokemon").param("size", "3")).andExpect(status().isOk());

        assertThat(upstreamCallsAfterFirst).isEqualTo(4); // 1 list + 3 details
        assertThat(totalHits()).isEqualTo(upstreamCallsAfterFirst);
    }

    @Test
    void unknownPokemonIs404AndInvalidIdentifierIs400() throws Exception {
        mvc.perform(get("/api/v1/pokemon/missingno"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POKEMON_NOT_FOUND"));
        mvc.perform(get("/api/v1/pokemon/bad!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_POKEMON_IDENTIFIER"));
        mvc.perform(get("/api/v1/pokemon").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
    }

    @Test
    void anUpstreamFailureBecomes502AndASlowUpstreamBecomes504() throws Exception {
        mvc.perform(get("/api/v1/pokemon/glitch"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
        mvc.perform(get("/api/v1/pokemon/slowpoke"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("CATALOG_TIMEOUT"));
    }

    private static int totalHits() {
        return HITS.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    // ---- stand-in for PokeAPI ----

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/api/v2/", PokemonFlowIT::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().substring("/api/v2".length());
        HITS.computeIfAbsent(path + "?" + exchange.getRequestURI().getQuery(), k -> new AtomicInteger()).incrementAndGet();
        try {
            switch (path) {
                case "/pokemon" -> respond(exchange, 200, fixture("list.json"));
                case "/pokemon/25", "/pokemon/pikachu" -> respond(exchange, 200, fixture("pokemon-25.json"));
                case "/pokemon/1", "/pokemon/2", "/pokemon/3" -> respond(exchange, 200, asPokemon(path.substring(9)));
                case "/pokemon-species/25" -> respond(exchange, 200, fixture("species-25.json"));
                case "/evolution-chain/10" -> respond(exchange, 200, fixture("evolution-10.json"));
                case "/pokemon/glitch" -> respond(exchange, 503, "{}");
                case "/pokemon/slowpoke" -> {
                    Thread.sleep(1500);
                    respond(exchange, 200, fixture("pokemon-25.json"));
                }
                default -> respond(exchange, 404, "Not Found");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String asPokemon(String id) {
        return fixture("pokemon-25.json").replaceFirst("\"id\": 25", "\"id\": " + id);
    }

    private static String fixture(String name) {
        try (InputStream in = PokemonFlowIT.class.getResourceAsStream("/pokeapi/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
