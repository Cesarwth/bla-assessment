package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.application.port.out.AuthToken;

public interface AuthenticateUserUseCase {
    AuthToken authenticate(AuthenticateCommand command);
}
