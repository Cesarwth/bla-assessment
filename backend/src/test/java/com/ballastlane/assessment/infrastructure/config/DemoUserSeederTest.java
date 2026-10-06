package com.ballastlane.assessment.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.in.RegisterUserCommand;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoUserSeederTest {

    @Mock RegisterUserUseCase registerUser;
    @Mock UserRepository users;
    @Mock PasswordHasher hasher;

    private DemoUserSeeder seeder(boolean enabled, String adminPassword) {
        return new DemoUserSeeder(registerUser, users, hasher,
                new DemoUserProperties(enabled, "demo", "demo@pokemon.dev", "Demo12345",
                        "demo-admin", "admin@pokemon.dev", adminPassword));
    }

    @Test
    void doesNothingWhenDisabled() {
        seeder(false, "Admin12345").run(null);

        verifyNoInteractions(registerUser, users, hasher);
    }

    @Test
    void registersDemoUserThroughTheUseCaseWhenEnabled() {
        when(users.existsByUsername("demo")).thenReturn(false);

        seeder(true, null).run(null);

        verify(registerUser).register(new RegisterUserCommand("demo", "demo@pokemon.dev", "Demo12345"));
    }

    @Test
    void isIdempotentWhenUserAlreadyExists() {
        when(users.existsByUsername("demo")).thenReturn(true);

        seeder(true, null).run(null);

        verify(registerUser, never()).register(any());
    }

    @Test
    void noDemoUserIsSeededWithoutAConfiguredPassword() {
        new DemoUserSeeder(registerUser, users, hasher,
                new DemoUserProperties(true, "demo", "demo@pokemon.dev", "", "demo-admin", "admin@pokemon.dev", null)).run(null);
        new DemoUserSeeder(registerUser, users, hasher,
                new DemoUserProperties(true, "demo", "demo@pokemon.dev", null, "demo-admin", "admin@pokemon.dev", null)).run(null);

        verifyNoInteractions(registerUser);
        verify(users, never()).save(any());
    }

    @Test
    void seedsTheAdminWithTheAdminRoleAndAHashedPassword() {
        when(users.existsByUsername("demo")).thenReturn(true);
        when(users.existsByUsername("demo-admin")).thenReturn(false);
        when(hasher.hash("Admin12345")).thenReturn("hashed-admin");

        seeder(true, "Admin12345").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().username()).isEqualTo("demo-admin");
        assertThat(saved.getValue().role()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().passwordHash()).isEqualTo("hashed-admin");
    }

    @Test
    void adminSeedIsIdempotent() {
        when(users.existsByUsername("demo")).thenReturn(true);
        when(users.existsByUsername("demo-admin")).thenReturn(true);

        seeder(true, "Admin12345").run(null);

        verify(users, never()).save(any());
    }

    @Test
    void noAdminIsSeededWithoutAConfiguredPassword() {
        when(users.existsByUsername("demo")).thenReturn(true);

        seeder(true, "").run(null);
        seeder(true, null).run(null);

        verify(users, never()).save(any());
    }
}
