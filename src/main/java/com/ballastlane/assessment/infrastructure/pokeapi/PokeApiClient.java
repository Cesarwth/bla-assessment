package com.ballastlane.assessment.infrastructure.pokeapi;

import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiEvolutionChain;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPage;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiSpecies;
import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin typed access to PokeAPI. Paths are built from validated ids/names only and never from URLs found in payloads.
 * Only successful answers are cached: 404s and failures are not, so a transient outage is not remembered.
 */
public class PokeApiClient {

    private final RestClient http;

    public PokeApiClient(RestClient http) {
        this.http = http;
    }

    @Cacheable(cacheNames = PokeApiCaches.PAGES)
    public PokeApiPage getPage(int limit, int offset) {
        return fetch(client -> client.get()
                        .uri(uri -> uri.path("/pokemon").queryParam("limit", limit).queryParam("offset", offset).build())
                        .retrieve()
                        .body(PokeApiPage.class))
                .orElseThrow(() -> new CatalogUnavailableException("PokeAPI returned no list", false, null));
    }

    @Cacheable(cacheNames = PokeApiCaches.POKEMON, unless = "#result == null")
    public Optional<PokeApiPokemon> getPokemon(String idOrName) {
        return fetch(client -> client.get().uri("/pokemon/{id}", idOrName).retrieve().body(PokeApiPokemon.class));
    }

    @Cacheable(cacheNames = PokeApiCaches.SPECIES, unless = "#result == null")
    public Optional<PokeApiSpecies> getSpecies(int id) {
        return fetch(client -> client.get().uri("/pokemon-species/{id}", id).retrieve().body(PokeApiSpecies.class));
    }

    @Cacheable(cacheNames = PokeApiCaches.EVOLUTION_CHAINS, unless = "#result == null")
    public Optional<PokeApiEvolutionChain> getEvolutionChain(int id) {
        return fetch(client -> client.get().uri("/evolution-chain/{id}", id).retrieve().body(PokeApiEvolutionChain.class));
    }

    private <T> Optional<T> fetch(Function<RestClient, T> call) {
        try {
            return Optional.ofNullable(call.apply(http));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientResponseException e) {
            throw new CatalogUnavailableException("PokeAPI answered " + e.getStatusCode().value(), false, e);
        } catch (ResourceAccessException e) {
            throw new CatalogUnavailableException("PokeAPI is unreachable", isTimeout(e), e);
        } catch (RestClientException e) {
            throw new CatalogUnavailableException("PokeAPI response could not be read", false, e);
        }
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
