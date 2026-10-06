package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.Actor;
import java.util.List;

public interface ListStoredPokemonIdsUseCase {
    /** The PokeAPI ids in the actor's own collection, ascending; lets clients mark what is already owned without probing. */
    List<Integer> list(Actor actor);
}
