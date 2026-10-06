package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.StoredPokemon;

public interface GetStoredPokemonUseCase {
    /** Looks only in the actor's own collection. */
    StoredPokemon get(int pokeApiId, Actor actor);
}
