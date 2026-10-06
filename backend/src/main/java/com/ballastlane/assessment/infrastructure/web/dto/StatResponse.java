package com.ballastlane.assessment.infrastructure.web.dto;

import com.ballastlane.assessment.domain.PokemonStat;

public record StatResponse(String name, int baseValue) {

    public static StatResponse from(PokemonStat stat) {
        return new StatResponse(stat.name(), stat.baseValue());
    }
}
