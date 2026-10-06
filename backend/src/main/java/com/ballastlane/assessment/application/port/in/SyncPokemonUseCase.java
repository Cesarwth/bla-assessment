package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.SyncResult;

public interface SyncPokemonUseCase {
    /** A newly created record is owned by {@code actor}; refreshing an existing one never changes its owner. */
    SyncResult sync(String idOrName, Actor actor);
}
