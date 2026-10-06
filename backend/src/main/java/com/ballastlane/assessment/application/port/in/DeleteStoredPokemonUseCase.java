package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.Actor;

public interface DeleteStoredPokemonUseCase {
    /** Removes the entry from the actor's own collection; an entry the actor does not have is simply not found. */
    void delete(int pokeApiId, Actor actor);
}
