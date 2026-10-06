package com.ballastlane.assessment.infrastructure.security;

import com.ballastlane.assessment.domain.AuthToken;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder encoder, Duration ttl, Clock clock) {
        this.encoder = encoder;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public AuthToken issue(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.username())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("uid", user.id())
                .claim("role", user.role().name())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AuthToken(token, expiresAt);
    }
}
