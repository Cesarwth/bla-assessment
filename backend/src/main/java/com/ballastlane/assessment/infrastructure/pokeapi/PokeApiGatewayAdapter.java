package com.ballastlane.assessment.infrastructure.pokeapi;

import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonSearchQuery;
import com.ballastlane.assessment.domain.PokemonSummary;
import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiEvolutionChain;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPage;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiSpecies;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/** Orchestrates the client and the mapper; it is the only implementation of the PokeApiGateway port. */
public class PokeApiGatewayAdapter implements PokeApiGateway {

    // Shared across requests: caps how many calls we have in flight against PokeAPI at any moment.
    private static final int MAX_CONCURRENT_CALLS = 10;

    private final PokeApiClient client;
    private final PokeApiMapper mapper;
    private final Executor executor;
    private final Semaphore inFlight = new Semaphore(MAX_CONCURRENT_CALLS);

    public PokeApiGatewayAdapter(PokeApiClient client, PokeApiMapper mapper, Executor executor) {
        this.client = client;
        this.mapper = mapper;
        this.executor = executor;
    }

    @Override
    public PageResult<PokemonSummary> findPage(int page, int size) {
        int offset = (int) Math.min((long) page * size, Integer.MAX_VALUE);
        PokeApiPage upstream = client.getPage(size, offset);
        List<PokeApiPage.Entry> entries = upstream.results() == null ? List.of() : upstream.results();

        return new PageResult<>(fetchSummaries(entries), page, size, upstream.count());
    }

    @Override
    public PageResult<PokemonSummary> search(PokemonSearchQuery query, int page, int size) {
        PokeApiPage index = client.getNameIndex();
        List<PokeApiPage.Entry> matches = (index.results() == null ? List.<PokeApiPage.Entry>of() : index.results())
                .stream()
                .map(entry -> new Match(mapper.idFromUrl(entry.url()), entry))
                .filter(match -> query.matches(match.id(), match.entry().name()))
                .sorted(Comparator.comparingInt(Match::id))
                .map(Match::entry)
                .toList();
        long from = Math.min((long) page * size, matches.size());
        long to = Math.min(from + size, matches.size());
        List<PokeApiPage.Entry> pageEntries = matches.subList((int) from, (int) to);
        return new PageResult<>(fetchSummaries(pageEntries), page, size, matches.size());
    }

    // The list endpoints only have names and URLs, so each row needs its own detail call; run them in parallel.
    private List<PokemonSummary> fetchSummaries(List<PokeApiPage.Entry> entries) {
        List<CompletableFuture<PokemonSummary>> calls = entries.stream()
                .map(entry -> CompletableFuture.supplyAsync(() -> fetchSummary(entry), executor))
                .toList();
        return calls.stream().map(PokeApiGatewayAdapter::await).toList();
    }

    private record Match(int id, PokeApiPage.Entry entry) {
    }

    @Override
    public Optional<PokemonDetail> findDetail(String idOrName) {
        Optional<PokeApiPokemon> pokemon = client.getPokemon(idOrName);
        if (pokemon.isEmpty()) {
            return Optional.empty();
        }
        // The species id differs from the pokemon id for alternate forms (e.g. deoxys-attack), so follow the payload.
        PokeApiSpecies species = client.getSpecies(mapper.speciesIdOf(pokemon.get())).orElse(null);
        PokeApiEvolutionChain chain = species == null
                ? null
                : mapper.chainIdOf(species).flatMap(client::getEvolutionChain).orElse(null);
        return Optional.of(mapper.toDetail(pokemon.get(), species, chain));
    }

    private PokemonSummary fetchSummary(PokeApiPage.Entry entry) {
        try {
            inFlight.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogUnavailableException("Interrupted while calling PokeAPI", false, e);
        }
        try {
            String id = String.valueOf(mapper.idFromUrl(entry.url()));
            return client.getPokemon(id)
                    .map(mapper::toSummary)
                    .orElseThrow(() -> new CatalogUnavailableException("Listed Pokemon " + id + " could not be fetched", false, null));
        } finally {
            inFlight.release();
        }
    }

    private static PokemonSummary await(CompletableFuture<PokemonSummary> call) {
        try {
            return call.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }
}
