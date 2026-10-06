package com.ballastlane.assessment.application.port.out;

import com.ballastlane.assessment.domain.AuthToken;
import com.ballastlane.assessment.domain.User;

public interface TokenIssuer {
    AuthToken issue(User user);
}
