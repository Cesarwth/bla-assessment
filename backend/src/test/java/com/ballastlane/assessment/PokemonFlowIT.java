package com.ballastlane.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.support.FakePokeApi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

    private static final FakePokeApi UPSTREAM = FakePokeApi.start();

    @DynamicPropertySource
    static void upstreamUrl(DynamicPropertyRegistry registry) {
        registry.add("app.pokeapi.base-url", UPSTREAM::baseUrl);
    }

    @Autowired MockMvc mvc;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void resetCachesAndCounters() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        UPSTREAM.resetHits();
    }

    @AfterEach
    void restoreIndex() {
        UPSTREAM.setIndexDown(false);
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop();
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
        int upstreamCallsAfterFirst = UPSTREAM.totalHits();

        mvc.perform(get("/api/v1/pokemon").param("size", "3")).andExpect(status().isOk());

        assertThat(upstreamCallsAfterFirst).isEqualTo(4); // 1 list + 3 details
        assertThat(UPSTREAM.totalHits()).isEqualTo(upstreamCallsAfterFirst);
    }

    @Test
    void filtersTheCatalogByNameSubstringAndPagesTheMatches() throws Exception {
        mvc.perform(get("/api/v1/pokemon").param("q", "  SAUR ").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[1].id").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/pokemon").param("q", "saur").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(3));
    }

    @Test
    void filtersByIdAndFindsHyphenatedNamesTypedWithASpace() throws Exception {
        mvc.perform(get("/api/v1/pokemon").param("q", "25"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(25))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/pokemon").param("q", "mr mime"))
                .andExpect(jsonPath("$.items[0].id").value(122));
    }

    @Test
    void aFilterWithoutMatchesIsAnEmptyPage() throws Exception {
        for (String q : new String[] {"zzz", "999", "%", ".*", "[a-z]+"}) {
            mvc.perform(get("/api/v1/pokemon").param("q", q))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.totalItems").value(0));
        }
    }

    @Test
    void aFilterPageBeyondTheLastKeepsTheTotals() throws Exception {
        mvc.perform(get("/api/v1/pokemon").param("q", "saur").param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void aTooLongFilterIs400AndTheSizeLimitStillApplies() throws Exception {
        mvc.perform(get("/api/v1/pokemon").param("q", "a".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
        mvc.perform(get("/api/v1/pokemon").param("q", "saur").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
    }

    @Test
    void theNameIndexIsFetchedOnceForTwoSearches() throws Exception {
        mvc.perform(get("/api/v1/pokemon").param("q", "saur")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/pokemon").param("q", "mew")).andExpect(status().isOk());

        // 1 index + details of 1,2,3 + detail of 150
        assertThat(UPSTREAM.totalHits()).isEqualTo(5);
    }

    @Test
    void anUnavailableNameIndexBecomes502AndIsNotRemembered() throws Exception {
        UPSTREAM.setIndexDown(true);
        mvc.perform(get("/api/v1/pokemon").param("q", "saur"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));

        UPSTREAM.setIndexDown(false);
        mvc.perform(get("/api/v1/pokemon").param("q", "saur")).andExpect(status().isOk());
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
}
