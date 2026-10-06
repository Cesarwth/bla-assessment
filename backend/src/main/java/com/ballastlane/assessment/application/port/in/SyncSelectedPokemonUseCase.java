package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.SyncReport;
import java.util.List;

public interface SyncSelectedPokemonUseCase {
    SyncReport sync(List<Integer> ids, Actor actor);
}
