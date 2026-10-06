package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.User;

public interface RegisterUserUseCase {
    User register(RegisterUserCommand command);
}
