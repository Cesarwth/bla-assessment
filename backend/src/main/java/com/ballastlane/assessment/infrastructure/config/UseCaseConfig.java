package com.ballastlane.assessment.infrastructure.config;

import com.ballastlane.assessment.application.port.in.AuthenticateUserUseCase;
import com.ballastlane.assessment.application.port.in.DeleteStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.GetPokemonDetailUseCase;
import com.ballastlane.assessment.application.port.in.GetStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonIdsUseCase;
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
import com.ballastlane.assessment.application.usecase.AuthenticateUser;
import com.ballastlane.assessment.application.usecase.DeleteStoredPokemon;
import com.ballastlane.assessment.application.usecase.GetPokemonDetail;
import com.ballastlane.assessment.application.usecase.GetStoredPokemon;
import com.ballastlane.assessment.application.usecase.ListPokemon;
import com.ballastlane.assessment.application.usecase.ListStoredPokemon;
import com.ballastlane.assessment.application.usecase.ListStoredPokemonIds;
import com.ballastlane.assessment.application.usecase.RegisterUser;
import com.ballastlane.assessment.application.usecase.SyncPokemon;
import com.ballastlane.assessment.application.usecase.SyncPokemonRange;
import com.ballastlane.assessment.application.usecase.UpdateStoredPokemon;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Application use cases are plain Java; this is the only place where they meet Spring. */
@Configuration
@EnableConfigurationProperties(DemoUserProperties.class)
public class UseCaseConfig {

    @Bean
    RegisterUserUseCase registerUserUseCase(UserRepository users, PasswordHasher hasher) {
        return new RegisterUser(users, hasher);
    }

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(UserRepository users, PasswordHasher hasher, TokenIssuer tokens) {
        return new AuthenticateUser(users, hasher, tokens);
    }

    @Bean
    ListPokemonUseCase listPokemonUseCase(PokeApiGateway gateway) {
        return new ListPokemon(gateway);
    }

    @Bean
    GetPokemonDetailUseCase getPokemonDetailUseCase(PokeApiGateway gateway) {
        return new GetPokemonDetail(gateway);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SyncPokemonUseCase syncPokemonUseCase(PokeApiGateway gateway, PokemonRepository repository, Clock clock) {
        return new SyncPokemon(gateway, repository, clock);
    }

    @Bean
    SyncPokemonRangeUseCase syncPokemonRangeUseCase(SyncPokemonUseCase syncPokemon) {
        return new SyncPokemonRange(syncPokemon);
    }

    @Bean
    ListStoredPokemonUseCase listStoredPokemonUseCase(PokemonRepository repository) {
        return new ListStoredPokemon(repository);
    }

    @Bean
    ListStoredPokemonIdsUseCase listStoredPokemonIdsUseCase(PokemonRepository repository) {
        return new ListStoredPokemonIds(repository);
    }

    @Bean
    GetStoredPokemonUseCase getStoredPokemonUseCase(PokemonRepository repository) {
        return new GetStoredPokemon(repository);
    }

    @Bean
    UpdateStoredPokemonUseCase updateStoredPokemonUseCase(PokemonRepository repository) {
        return new UpdateStoredPokemon(repository);
    }

    @Bean
    DeleteStoredPokemonUseCase deleteStoredPokemonUseCase(PokemonRepository repository) {
        return new DeleteStoredPokemon(repository);
    }

    @Bean
    @Order(1) // accounts first: the demo Pokémon are owned by them
    @ConditionalOnProperty(prefix = "app.seed.demo-user", name = "enabled", havingValue = "true")
    DemoUserSeeder demoUserSeeder(
            RegisterUserUseCase registerUser, UserRepository users, PasswordHasher hasher, DemoUserProperties properties) {
        return new DemoUserSeeder(registerUser, users, hasher, properties);
    }

    @Bean
    @Order(2)
    @ConditionalOnProperty(prefix = "app.seed.demo-data", name = "enabled", havingValue = "true")
    DemoDataSeeder demoDataSeeder(PokemonRepository repository, UserRepository users, Clock clock) {
        return new DemoDataSeeder(new DemoDataReader().read(), repository, users, clock);
    }
}
