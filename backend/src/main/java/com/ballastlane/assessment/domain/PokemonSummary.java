package com.ballastlane.assessment.domain;

import java.util.List;

/** Row of the paginated list: sprite, category (types), mass and skills (abilities). */
public record PokemonSummary(
        int id, String name, String spriteUrl, String imageUrl, List<String> types, double weightKg, List<String> abilities) {

    public PokemonSummary {
        types = List.copyOf(types);
        abilities = List.copyOf(abilities);
    }
}
