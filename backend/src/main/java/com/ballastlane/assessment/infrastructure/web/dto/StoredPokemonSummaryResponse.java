package com.ballastlane.assessment.infrastructure.web.dto;

import com.ballastlane.assessment.domain.StoredPokemon;
import java.util.List;

public record StoredPokemonSummaryResponse(
        int id,
        String name,
        String spriteUrl,
        List<String> types,
        double weightKg,
        List<String> abilities,
        String nickname,
        String region,
        List<String> tags,
        Long ownerId) {

    public static StoredPokemonSummaryResponse from(StoredPokemon pokemon) {
        return new StoredPokemonSummaryResponse(pokemon.pokeApiId(), pokemon.name(), pokemon.spriteUrl(), pokemon.types(),
                pokemon.weightKg(), pokemon.abilities(), pokemon.localAttributes().nickname(),
                pokemon.localAttributes().region(), pokemon.localAttributes().tags(), pokemon.ownerId());
    }
}
