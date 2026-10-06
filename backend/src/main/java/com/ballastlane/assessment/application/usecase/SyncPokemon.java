package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonIdentifier;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.SyncOutcome;
import com.ballastlane.assessment.domain.SyncResult;
import com.ballastlane.assessment.domain.exception.DuplicateStoredPokemonException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class SyncPokemon implements SyncPokemonUseCase {

    // Bounded so that a persistent conflict surfaces as an error instead of looping.
    static final int MAX_ATTEMPTS = 3;

    private final PokeApiGateway gateway;
    private final PokemonRepository repository;
    private final Clock clock;

    public SyncPokemon(PokeApiGateway gateway, PokemonRepository repository, Clock clock) {
        this.gateway = gateway;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public SyncResult sync(String idOrName, Actor actor) {
        PokemonIdentifier identifier = PokemonIdentifier.of(idOrName);
        PokemonDetail detail = gateway.findDetail(identifier.value())
                .orElseThrow(() -> new PokemonNotFoundException(identifier.value()));
        Instant now = clock.instant();

        // Upsert keyed by the PokeAPI id (not the name): the same Pokemon can be requested as "25" or "pikachu".
        // Sync is idempotent (it only rewrites PokeAPI-owned data), so losing a race against another sync of the same
        // Pokemon is not an error: re-read the winner's row and apply the refresh on top of it.
        for (int attempt = 1; ; attempt++) {
            try {
                return save(repository.findByPokeApiId(detail.id()), detail, now, actor);
            } catch (DuplicateStoredPokemonException | StaleStoredPokemonException raced) {
                if (attempt == MAX_ATTEMPTS) {
                    throw raced;
                }
            }
        }
    }

    private SyncResult save(Optional<StoredPokemon> existing, PokemonDetail detail, Instant now, Actor actor) {
        StoredPokemon toSave = existing
                .map(stored -> stored.refreshedFrom(detail, now))
                .orElseGet(() -> StoredPokemon.fromDetail(detail, now, actor));
        return new SyncResult(existing.isPresent() ? SyncOutcome.UPDATED : SyncOutcome.CREATED, repository.save(toSave));
    }
}
