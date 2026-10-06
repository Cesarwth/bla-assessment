package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.User;

public interface GetProfileUseCase {
    User get(Actor actor);
}
