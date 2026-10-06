package com.ballastlane.assessment.infrastructure.config;

import com.ballastlane.assessment.application.port.in.RegisterUserCommand;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.application.port.out.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

// Goes through the use case so the password is BCrypt-hashed at runtime: no hash is committed to git.
public class DemoUserSeeder implements ApplicationRunner {

    private final RegisterUserUseCase registerUser;
    private final UserRepository users;
    private final DemoUserProperties properties;

    public DemoUserSeeder(RegisterUserUseCase registerUser, UserRepository users, DemoUserProperties properties) {
        this.registerUser = registerUser;
        this.users = users;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || users.existsByUsername(properties.username())) {
            return;
        }
        registerUser.register(new RegisterUserCommand(properties.username(), properties.email(), properties.password()));
    }
}
