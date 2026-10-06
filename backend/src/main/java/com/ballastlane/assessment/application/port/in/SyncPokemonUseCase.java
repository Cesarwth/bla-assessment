package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.SyncResult;

public interface SyncPokemonUseCase {
    /** Adds the Pokemon to the actor's collection (CREATED) or refreshes its data there (UPDATED, notes untouched). */
    SyncResult sync(String idOrName, Actor actor);
}
