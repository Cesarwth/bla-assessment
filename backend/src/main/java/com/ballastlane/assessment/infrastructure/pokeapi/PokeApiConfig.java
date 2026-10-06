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
    static RestClient.Builder restClientBuilder(PokeApiProperties props) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build());
        factory.setReadTimeout(props.readTimeout());
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .requestFactory(factory);
    }

    static CacheManager cacheManager(PokeApiProperties props) {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                PokeApiCaches.PAGES, PokeApiCaches.NAME_INDEX, PokeApiCaches.POKEMON, PokeApiCaches.SPECIES, PokeApiCaches.EVOLUTION_CHAINS);
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(props.cacheTtl()).maximumSize(props.cacheMaxSize()));
        return manager;
    }

    @Bean
    CacheManager pokeApiCacheManager(PokeApiProperties props) {
        return cacheManager(props);
    }

    @Bean
    PokeApiClient pokeApiClient(PokeApiProperties props) {
        return new PokeApiClient(restClientBuilder(props).build());
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
