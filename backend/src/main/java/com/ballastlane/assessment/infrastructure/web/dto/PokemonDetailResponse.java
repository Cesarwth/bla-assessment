package com.ballastlane.assessment.infrastructure.web.dto;

import com.ballastlane.assessment.domain.PokemonDetail;
import java.util.List;

public record PokemonDetailResponse(
        int id,
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
        EvolutionNodeResponse evolutionChain) {

    public static PokemonDetailResponse from(PokemonDetail detail) {
        return new PokemonDetailResponse(detail.id(), detail.name(), detail.imageUrl(), detail.spriteUrl(),
                detail.types(), detail.weightKg(), detail.heightM(), detail.abilities(),
                detail.stats().stream().map(StatResponse::from).toList(), detail.genus(), detail.description(),
                detail.evolutionChain() == null ? null : EvolutionNodeResponse.from(detail.evolutionChain()));
    }
}
