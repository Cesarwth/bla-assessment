package com.ballastlane.assessment.infrastructure.pokeapi;

import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableCaching
@EnableConfigurationProperties(PokeApiProperties.class)
public class PokeApiConfig {

    private static final String USER_AGENT = "bla-assessment/0.1 (interview exercise)";

    // Static factories so tests can build the exact production client and bind a stub server to it.
    static RestClient.Builder restClientBuilder(PokeApiProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .requestFactory(factory);
    }

    static CacheManager cacheManager(PokeApiProperties properties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                PokeApiCaches.PAGES, PokeApiCaches.NAME_INDEX, PokeApiCaches.POKEMON, PokeApiCaches.SPECIES, PokeApiCaches.EVOLUTION_CHAINS);
        cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(properties.cacheTtl()).maximumSize(properties.cacheMaxSize()));
        return cacheManager;
    }

    @Bean
    CacheManager pokeApiCacheManager(PokeApiProperties properties) {
        return cacheManager(properties);
    }

    @Bean
    PokeApiClient pokeApiClient(PokeApiProperties properties) {
        return new PokeApiClient(restClientBuilder(properties).build());
    }

    @Bean(destroyMethod = "close")
    ExecutorService pokeApiExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    PokeApiGateway pokeApiGateway(PokeApiClient client, @Qualifier("pokeApiExecutor") ExecutorService executor) {
        return new PokeApiGatewayAdapter(client, new PokeApiMapper(), executor);
    }
}
