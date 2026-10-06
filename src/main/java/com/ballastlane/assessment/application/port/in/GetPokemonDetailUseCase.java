package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.PokemonDetail;

public interface GetPokemonDetailUseCase {
    PokemonDetail get(String idOrName);
}
