package com.ballastlane.assessment.infrastructure.web;

import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.Role;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

/** The only place that reads identity claims, so use cases stay free of tokens and Spring Security. */
final class ActorResolver {

    private ActorResolver() {
    }

    static Actor from(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        String role = jwt.getClaimAsString("role");
        if (!(uid instanceof Number id) || jwt.getSubject() == null || role == null) {
            throw new BadCredentialsException("The token does not identify a user");
        }
        try {
            return new Actor(id.longValue(), jwt.getSubject(), Role.valueOf(role));
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("The token carries an unknown role", e);
        }
    }
}
