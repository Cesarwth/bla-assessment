package com.ballastlane.assessment.application.port.out;

import com.ballastlane.assessment.domain.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    User save(User user);
}
