package com.ballastlane.assessment.infrastructure.web.dto;

import com.ballastlane.assessment.domain.PokemonSummary;
import java.util.List;

public record PokemonSummaryResponse(
        int id, String name, String spriteUrl, String imageUrl, List<String> types, double weightKg, List<String> abilities) {

    public static PokemonSummaryResponse from(PokemonSummary summary) {
        return new PokemonSummaryResponse(summary.id(), summary.name(), summary.spriteUrl(), summary.imageUrl(),
                summary.types(),
                summary.weightKg(), summary.abilities());
    }
}
