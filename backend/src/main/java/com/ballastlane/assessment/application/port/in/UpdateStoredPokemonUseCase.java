package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.StoredPokemon;

public interface UpdateStoredPokemonUseCase {
    StoredPokemon update(UpdateStoredPokemonCommand command);
}
