package com.ballastlane.assessment.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

class ActorResolverTest {

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "HS256").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void buildsTheActorFromTheClaimsTheIssuerPutsInEveryToken() {
        Jwt jwt = token().subject("ash").claim("uid", 5L).claim("role", "ADMIN").build();

        assertThat(ActorResolver.from(jwt)).isEqualTo(new Actor(5L, "ash", Role.ADMIN));
    }

    @Test
    void acceptsAnIntegerUidAsJsonParsersMayProduceIt() {
        Jwt jwt = token().subject("ash").claim("uid", 5).claim("role", "USER").build();

        assertThat(ActorResolver.from(jwt).userId()).isEqualTo(5L);
    }

    @Test
    void aTokenWithoutUidIsAnAuthenticationProblem() {
        Jwt jwt = token().subject("ash").claim("role", "USER").build();

        assertThatThrownBy(() -> ActorResolver.from(jwt)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void aTokenWithAnUnknownRoleOrNoSubjectIsRejectedToo() {
        assertThatThrownBy(() -> ActorResolver.from(token().subject("ash").claim("uid", 5L).claim("role", "GOD").build()))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> ActorResolver.from(token().claim("uid", 5L).claim("role", "USER").build()))
                .isInstanceOf(BadCredentialsException.class);
    }
}
