package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.SyncReport;

public interface SyncPokemonRangeUseCase {
    SyncReport sync(int from, int to);
}
