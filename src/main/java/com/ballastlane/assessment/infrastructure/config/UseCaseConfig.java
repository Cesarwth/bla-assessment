package com.ballastlane.assessment.infrastructure.config;

import com.ballastlane.assessment.application.port.in.AuthenticateUserUseCase;
import com.ballastlane.assessment.application.port.in.GetPokemonDetailUseCase;
import com.ballastlane.assessment.application.port.in.ListPokemonUseCase;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.application.usecase.AuthenticateUser;
import com.ballastlane.assessment.application.usecase.GetPokemonDetail;
import com.ballastlane.assessment.application.usecase.ListPokemon;
import com.ballastlane.assessment.application.usecase.RegisterUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    @ConditionalOnProperty(prefix = "app.seed.demo-user", name = "enabled", havingValue = "true")
    DemoUserSeeder demoUserSeeder(RegisterUserUseCase registerUser, UserRepository users, DemoUserProperties properties) {
        return new DemoUserSeeder(registerUser, users, properties);
    }
}
