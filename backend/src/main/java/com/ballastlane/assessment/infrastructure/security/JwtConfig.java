package com.ballastlane.assessment.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    static JwtEncoder encoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return encoder(key(properties.secret()));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withSecretKey(key(properties.secret())).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    TokenIssuer tokenIssuer(JwtEncoder encoder, JwtProperties properties) {
        return new JwtTokenIssuer(encoder, Duration.ofMinutes(properties.ttlMinutes()), Clock.systemUTC());
    }
}
