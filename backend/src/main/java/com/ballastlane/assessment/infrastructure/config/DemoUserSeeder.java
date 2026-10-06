package com.ballastlane.assessment.infrastructure.config;

import com.ballastlane.assessment.application.port.in.RegisterUserCommand;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.application.port.out.PasswordHasher;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

// Passwords are hashed at runtime (never committed): the user goes through the use case, the admin through the ports
// because registration always creates role USER.
public class DemoUserSeeder implements ApplicationRunner {

    private final RegisterUserUseCase registerUser;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final DemoUserProperties properties;

    public DemoUserSeeder(
            RegisterUserUseCase registerUser, UserRepository users, PasswordHasher hasher, DemoUserProperties properties) {
        this.registerUser = registerUser;
        this.users = users;
        this.hasher = hasher;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        seedUser();
        seedAdmin();
    }

    private void seedUser() {
        String password = properties.password();
        if (password == null || password.isBlank() || users.existsByUsername(properties.username())) {
            return;
        }
        registerUser.register(new RegisterUserCommand(properties.username(), properties.email(), password));
    }

    private void seedAdmin() {
        String password = properties.adminPassword();
        if (password == null || password.isBlank() || users.existsByUsername(properties.adminUsername())) {
            return;
        }
        users.save(new User(null, properties.adminUsername(), properties.adminEmail(), hasher.hash(password), Role.ADMIN));
    }
}
