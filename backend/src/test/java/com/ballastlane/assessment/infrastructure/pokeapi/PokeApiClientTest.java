package com.ballastlane.assessment.infrastructure.pokeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiEvolutionChain;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPage;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiSpecies;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PokeApiClientTest {

    private static final String BASE = "https://pokeapi.co/api/v2";

    MockRestServiceServer server;
    PokeApiClient client;

    @BeforeEach
    void setUp() {
        PokeApiProperties properties = new PokeApiProperties(BASE, Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofHours(1), 100);
        RestClient.Builder builder = PokeApiConfig.restClientBuilder(properties);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PokeApiClient(builder.build());
    }

    @Test
    void fetchesAPokemonByIdOrName() {
        server.expect(requestTo(BASE + "/pokemon/25")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(Fixtures.read("pokemon-25.json"), MediaType.APPLICATION_JSON));

        Optional<PokeApiPokemon> foundPokemon = client.getPokemon("25");

        assertThat(foundPokemon).hasValueSatisfying(pokemon -> {
            assertThat(pokemon.id()).isEqualTo(25);
            assertThat(pokemon.name()).isEqualTo("pikachu");
            assertThat(pokemon.weight()).isEqualTo(60);
            assertThat(pokemon.types().get(0).type().name()).isEqualTo("electric");
            assertThat(pokemon.stats().get(0).baseStat()).isEqualTo(35);
            assertThat(pokemon.sprites().other().officialArtwork().frontDefault()).contains("official-artwork/25.png");
            assertThat(pokemon.species().url()).endsWith("/pokemon-species/25/");
        });
        server.verify();
    }

    @Test
    void buildsThePageUrlWithLimitAndOffset() {
        server.expect(requestTo(BASE + "/pokemon?limit=3&offset=6"))
                .andRespond(withSuccess(Fixtures.read("list.json"), MediaType.APPLICATION_JSON));

        PokeApiPage page = client.getPage(3, 6);

        assertThat(page.count()).isEqualTo(1351);
        assertThat(page.results()).extracting(PokeApiPage.Entry::name).containsExactly("bulbasaur", "ivysaur", "venusaur");
        server.verify();
    }

    @Test
    void fetchesTheWholeNameIndexInOneCall() {
        server.expect(requestTo(BASE + "/pokemon?limit=100000&offset=0"))
                .andRespond(withSuccess(Fixtures.read("list.json"), MediaType.APPLICATION_JSON));

        PokeApiPage index = client.getNameIndex();

        assertThat(index.results()).extracting(PokeApiPage.Entry::name).containsExactly("bulbasaur", "ivysaur", "venusaur");
        server.verify();
    }

    @Test
    void fetchesSpeciesAndEvolutionChain() {
        server.expect(requestTo(BASE + "/pokemon-species/25"))
                .andRespond(withSuccess(Fixtures.read("species-25.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/evolution-chain/10"))
                .andRespond(withSuccess(Fixtures.read("evolution-10.json"), MediaType.APPLICATION_JSON));

        PokeApiSpecies species = client.getSpecies(25).orElseThrow();
        PokeApiEvolutionChain chain = client.getEvolutionChain(10).orElseThrow();

        assertThat(species.evolutionChain().url()).endsWith("/evolution-chain/10/");
        assertThat(species.genera()).anyMatch(entry -> entry.genus().equals("Mouse Pokémon"));
        assertThat(chain.chain().species().name()).isEqualTo("pichu");
        assertThat(chain.chain().evolvesTo().get(0).evolvesTo().get(0).species().name()).isEqualTo("raichu");
        server.verify();
    }

    @Test
    void returnsEmptyWhenPokeApiAnswersNotFound() {
        server.expect(requestTo(BASE + "/pokemon/missingno")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getPokemon("missingno")).isEmpty();
    }

    @Test
    void throwsUnavailableOnServerErrors() {
        server.expect(requestTo(BASE + "/pokemon/25")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getPokemon("25"))
                .isInstanceOfSatisfying(CatalogUnavailableException.class, exception -> assertThat(exception.isTimeout()).isFalse());
    }

    @Test
    void throwsUnavailableOnUnexpectedClientErrors() {
        server.expect(requestTo(BASE + "/pokemon/25")).andRespond(withRawStatus(400));

        assertThatThrownBy(() -> client.getPokemon("25")).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void flagsTimeoutsSoTheWebLayerCanAnswerGatewayTimeout() {
        server.expect(requestTo(BASE + "/pokemon/25")).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.getPokemon("25"))
                .isInstanceOfSatisfying(CatalogUnavailableException.class, exception -> assertThat(exception.isTimeout()).isTrue());
    }

    @Test
    void throwsUnavailableOnMalformedPayloadInsteadOfLeakingParserErrors() {
        server.expect(requestTo(BASE + "/pokemon/25")).andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getPokemon("25")).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void identifiesItselfWithADescriptiveUserAgent() {
        server.expect(requestTo(BASE + "/pokemon/25"))
                .andExpect(header("User-Agent", org.hamcrest.Matchers.startsWith("bla-assessment/")))
                .andRespond(withSuccess(Fixtures.read("pokemon-25.json"), MediaType.APPLICATION_JSON));

        client.getPokemon("25");

        server.verify();
    }
}
