package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.SyncResult;

public interface SyncPokemonUseCase {
    SyncResult sync(String idOrName);
}
