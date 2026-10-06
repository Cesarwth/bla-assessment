package com.ballastlane.assessment.infrastructure.pokeapi;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.pokeapi")
public record PokeApiProperties(
        String baseUrl, Duration connectTimeout, Duration readTimeout, Duration cacheTtl, long cacheMaxSize) {
}
