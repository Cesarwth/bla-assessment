package com.ballastlane.assessment.infrastructure.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.in.RegisterUserCommand;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoUserSeederTest {

    @Mock RegisterUserUseCase registerUser;
    @Mock UserRepository users;

    private DemoUserSeeder seeder(boolean enabled) {
        return new DemoUserSeeder(registerUser, users,
                new DemoUserProperties(enabled, "demo", "demo@pokemon.dev", "Demo12345"));
    }

    @Test
    void doesNothingWhenDisabled() {
        seeder(false).run(null);

        verifyNoInteractions(registerUser, users);
    }

    @Test
    void registersDemoUserThroughTheUseCaseWhenEnabled() {
        when(users.existsByUsername("demo")).thenReturn(false);

        seeder(true).run(null);

        verify(registerUser).register(new RegisterUserCommand("demo", "demo@pokemon.dev", "Demo12345"));
    }

    @Test
    void isIdempotentWhenUserAlreadyExists() {
        when(users.existsByUsername("demo")).thenReturn(true);

        seeder(true).run(null);

        verify(registerUser, never()).register(any());
    }
}
