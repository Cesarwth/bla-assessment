package com.ballastlane.assessment.domain;

import com.ballastlane.assessment.domain.exception.InvalidUserException;

/** Who is performing an action. Built by the web layer from the token; the domain never sees tokens. */
public record Actor(Long userId, String username, Role role) {

    public Actor {
        if (userId == null || username == null || username.isBlank() || role == null) {
            throw new InvalidUserException("An actor needs an id, a username and a role");
        }
    }
}
