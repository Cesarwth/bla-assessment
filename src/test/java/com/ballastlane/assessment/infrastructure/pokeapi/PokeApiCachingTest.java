package com.ballastlane.assessment.infrastructure.pokeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@SpringJUnitConfig(PokeApiCachingTest.Config.class)
class PokeApiCachingTest {

    private static final String BASE = "https://pokeapi.co/api/v2";

    @Autowired PokeApiClient client;
    @Autowired MockRestServiceServer server;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        server.reset();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void secondCallForTheSamePokemonDoesNotHitUpstream() {
        server.expect(once(), requestTo(BASE + "/pokemon/25"))
                .andRespond(withSuccess(Fixtures.read("pokemon-25.json"), MediaType.APPLICATION_JSON));

        assertThat(client.getPokemon("25")).isPresent();
        assertThat(client.getPokemon("25")).isPresent();

        server.verify();
    }

    @Test
    void pagesAreCachedPerLimitAndOffset() {
        server.expect(once(), requestTo(BASE + "/pokemon?limit=3&offset=0"))
                .andRespond(withSuccess(Fixtures.read("list.json"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/pokemon?limit=3&offset=3"))
                .andRespond(withSuccess(Fixtures.read("list.json"), MediaType.APPLICATION_JSON));

        client.getPage(3, 0);
        client.getPage(3, 0);
        client.getPage(3, 3);

        server.verify();
    }

    @Test
    void notFoundIsNotCached() {
        server.expect(times(2), requestTo(BASE + "/pokemon/missingno")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getPokemon("missingno")).isEmpty();
        assertThat(client.getPokemon("missingno")).isEmpty();

        server.verify();
    }

    @Test
    void failuresAreNotCachedSoTheNextCallCanRecover() {
        server.expect(once(), requestTo(BASE + "/pokemon/25")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(once(), requestTo(BASE + "/pokemon/25"))
                .andRespond(withSuccess(Fixtures.read("pokemon-25.json"), MediaType.APPLICATION_JSON));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.getPokemon("25")).isNotNull();
        assertThat(client.getPokemon("25")).isPresent();

        server.verify();
    }

    @Configuration
    @EnableCaching
    static class Config {

        private final PokeApiProperties props =
                new PokeApiProperties(BASE, Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofHours(1), 100);
        private final RestClient.Builder builder = PokeApiConfig.restClientBuilder(props);
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        @Bean MockRestServiceServer mockServer() { return server; }

        @Bean CacheManager cacheManager() { return PokeApiConfig.cacheManager(props); }

        @Bean PokeApiClient pokeApiClient() { return new PokeApiClient(builder.build()); }
    }
}
