package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.Actor;

public interface DeleteStoredPokemonUseCase {
    /** Reserved to admins: the rule is enforced here, not only by the route rules of the web layer. */
    void delete(int pokeApiId, Actor actor);
}
