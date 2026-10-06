package com.ballastlane.assessment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.application.port.out.AuthToken;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtTokenIssuerTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-32";
    private static final User ASH = new User(1L, "ash", "ash@pallet.town", "hashed", Role.USER);

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static JwtDecoder decoder(String secret) {
        return NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static JwtTokenIssuer issuer(Instant now, Duration ttl) {
        return new JwtTokenIssuer(JwtConfig.encoder(key(SECRET)), ttl, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuesATokenCarryingSubjectRoleAndExpiry() {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        AuthToken token = issuer(now, Duration.ofMinutes(30)).issue(ASH);

        Jwt jwt = decoder(SECRET).decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo("ash");
        assertThat(jwt.<String>getClaim("role")).isEqualTo("USER");
        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        AuthToken token = issuer(Instant.now(), Duration.ofMinutes(5)).issue(ASH);

        assertThatThrownBy(() -> decoder("another-secret-another-secret-32by").decode(token.value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        AuthToken token = issuer(Instant.now().minus(Duration.ofHours(1)), Duration.ofMinutes(5)).issue(ASH);

        assertThatThrownBy(() -> decoder(SECRET).decode(token.value())).isInstanceOf(JwtException.class);
    }
}
