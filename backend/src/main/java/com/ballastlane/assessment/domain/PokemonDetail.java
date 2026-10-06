package com.ballastlane.assessment.domain;

import java.util.List;

/** Everything the detail view needs; description and evolutionChain may be null when upstream has none. */
public record PokemonDetail(
        int id,
        String name,
        String imageUrl,
        String spriteUrl,
        List<String> types,
        double weightKg,
        double heightM,
        List<String> abilities,
        List<PokemonStat> stats,
        String genus,
        String description,
        EvolutionNode evolutionChain) {

    public PokemonDetail {
        types = List.copyOf(types);
        abilities = List.copyOf(abilities);
        stats = List.copyOf(stats);
    }
}
