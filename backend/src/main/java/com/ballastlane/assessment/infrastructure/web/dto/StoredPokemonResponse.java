package com.ballastlane.assessment.infrastructure.web.dto;

import com.ballastlane.assessment.domain.StoredPokemon;
import java.time.Instant;
import java.util.List;

/** The caller's collection entry; {@code version} is the entry's. {@code id} is the PokeAPI id (the identifier used in URLs); our internal row id is not exposed. */
public record StoredPokemonResponse(
        int id,
        Long version,
        String name,
        String imageUrl,
        String spriteUrl,
        List<String> types,
        double weightKg,
        double heightM,
        List<String> abilities,
        List<StatResponse> stats,
        String genus,
        String description,
        EvolutionNodeResponse evolutionChain,
        String nickname,
        String region,
        List<String> tags,
        Instant syncedAt) {

    public static StoredPokemonResponse from(StoredPokemon pokemon) {
        return new StoredPokemonResponse(pokemon.pokeApiId(), pokemon.version(), pokemon.name(), pokemon.imageUrl(),
                pokemon.spriteUrl(), pokemon.types(), pokemon.weightKg(), pokemon.heightM(), pokemon.abilities(),
                pokemon.stats().stream().map(StatResponse::from).toList(), pokemon.genus(), pokemon.description(),
                pokemon.evolutionChain() == null ? null : EvolutionNodeResponse.from(pokemon.evolutionChain()),
                pokemon.localAttributes().nickname(), pokemon.localAttributes().region(),
                pokemon.localAttributes().tags(), pokemon.syncedAt());
    }
}
