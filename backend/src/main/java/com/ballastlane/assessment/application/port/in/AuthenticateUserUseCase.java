package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.AuthToken;

public interface AuthenticateUserUseCase {
    AuthToken authenticate(AuthenticateCommand command);
}
