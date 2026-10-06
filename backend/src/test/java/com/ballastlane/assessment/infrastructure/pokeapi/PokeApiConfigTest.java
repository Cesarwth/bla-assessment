package com.ballastlane.assessment.infrastructure.pokeapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;

class PokeApiConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PokeApiConfig.class)
            .withPropertyValues(
                    "app.pokeapi.base-url=http://localhost:9/api/v2",
                    "app.pokeapi.connect-timeout=2s",
                    "app.pokeapi.read-timeout=5s",
                    "app.pokeapi.cache-ttl=1h",
                    "app.pokeapi.cache-max-size=10");

    @Test
    void wiresTheGatewayTheClientAndOneCachePerUpstreamResource() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PokeApiGateway.class);
            assertThat(context).hasSingleBean(PokeApiClient.class);
            CacheManager caches = context.getBean(CacheManager.class);
            assertThat(caches.getCacheNames()).containsExactlyInAnyOrder(
                    PokeApiCaches.PAGES, PokeApiCaches.NAME_INDEX, PokeApiCaches.POKEMON, PokeApiCaches.SPECIES, PokeApiCaches.EVOLUTION_CHAINS);
        });
    }

    @Test
    void bindsTheConfiguredTimeoutsAndCacheSize() {
        runner.run(context -> {
            PokeApiProperties props = context.getBean(PokeApiProperties.class);
            assertThat(props.baseUrl()).isEqualTo("http://localhost:9/api/v2");
            assertThat(props.connectTimeout().toSeconds()).isEqualTo(2);
            assertThat(props.readTimeout().toSeconds()).isEqualTo(5);
            assertThat(props.cacheMaxSize()).isEqualTo(10);
        });
    }

    @Test
    void theVirtualThreadExecutorIsShutDownWhenTheContextCloses() {
        AtomicReference<ExecutorService> executor = new AtomicReference<>();

        runner.run(context -> executor.set(context.getBean("pokeApiExecutor", ExecutorService.class)));

        // runner.run closes the context on return, which must trigger the bean's destroy method
        assertThat(executor.get().isShutdown()).isTrue();
    }
}
