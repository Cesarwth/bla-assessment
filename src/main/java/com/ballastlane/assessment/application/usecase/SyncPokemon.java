package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonIdentifier;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.SyncOutcome;
import com.ballastlane.assessment.domain.SyncResult;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class SyncPokemon implements SyncPokemonUseCase {

    private final PokeApiGateway gateway;
    private final PokemonRepository repository;
    private final Clock clock;

    public SyncPokemon(PokeApiGateway gateway, PokemonRepository repository, Clock clock) {
        this.gateway = gateway;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public SyncResult sync(String idOrName) {
        PokemonIdentifier identifier = PokemonIdentifier.of(idOrName);
        PokemonDetail detail = gateway.findDetail(identifier.value())
                .orElseThrow(() -> new PokemonNotFoundException(identifier.value()));
        Instant now = clock.instant();

        // Upsert keyed by the PokeAPI id (not the name): the same Pokemon can be requested as "25" or "pikachu".
        Optional<StoredPokemon> existing = repository.findByPokeApiId(detail.id());
        StoredPokemon toSave = existing
                .map(stored -> stored.refreshedFrom(detail, now))
                .orElseGet(() -> StoredPokemon.fromDetail(detail, now));
        StoredPokemon saved = repository.save(toSave);
        return new SyncResult(existing.isPresent() ? SyncOutcome.UPDATED : SyncOutcome.CREATED, saved);
    }
}
