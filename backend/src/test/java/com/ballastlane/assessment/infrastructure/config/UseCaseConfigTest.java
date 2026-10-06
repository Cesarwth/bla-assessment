package com.ballastlane.assessment.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ballastlane.assessment.application.port.in.AuthenticateUserUseCase;
import com.ballastlane.assessment.application.port.in.DeleteStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.GetPokemonDetailUseCase;
import com.ballastlane.assessment.application.port.in.GetStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonRangeUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The only place where use cases meet Spring: a missing or mis-typed bean must fail here, not at runtime. */
class UseCaseConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(UseCaseConfig.class)
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(PasswordHasher.class, () -> mock(PasswordHasher.class))
            .withBean(TokenIssuer.class, () -> mock(TokenIssuer.class))
            .withBean(PokeApiGateway.class, () -> mock(PokeApiGateway.class))
            .withBean(PokemonRepository.class, () -> mock(PokemonRepository.class));

    @Test
    void everyUseCasePortHasAnImplementation() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            for (Class<?> port : new Class<?>[] {
                    RegisterUserUseCase.class, AuthenticateUserUseCase.class, ListPokemonUseCase.class,
                    GetPokemonDetailUseCase.class, SyncPokemonUseCase.class, SyncPokemonRangeUseCase.class,
                    ListStoredPokemonUseCase.class, GetStoredPokemonUseCase.class, UpdateStoredPokemonUseCase.class,
                    DeleteStoredPokemonUseCase.class}) {
                assertThat(context).hasSingleBean(port);
            }
        });
    }

    @Test
    void theDemoSeederExistsOnlyWhenExplicitlyEnabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DemoUserSeeder.class));
        runner.withPropertyValues("app.seed.demo-user.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoUserSeeder.class));
        runner.withPropertyValues("app.seed.demo-user.enabled=true", "app.seed.demo-user.username=demo",
                        "app.seed.demo-user.email=demo@pokemon.dev", "app.seed.demo-user.password=Demo12345")
                .run(context -> assertThat(context).hasSingleBean(DemoUserSeeder.class));
    }

    @Test
    void theDemoDataSeederExistsOnlyWhenExplicitlyEnabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DemoDataSeeder.class));
        runner.withPropertyValues("app.seed.demo-data.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoDataSeeder.class));
        runner.withPropertyValues("app.seed.demo-data.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DemoDataSeeder.class));
    }
}
