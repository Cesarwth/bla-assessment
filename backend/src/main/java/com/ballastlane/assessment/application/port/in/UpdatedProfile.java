package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.AuthToken;
import com.ballastlane.assessment.domain.User;

/** The token is reissued because it carries the username, which may just have changed. */
public record UpdatedProfile(User user, AuthToken token) {
}
