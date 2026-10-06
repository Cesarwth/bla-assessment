package com.ballastlane.assessment.application.port.out;

import com.ballastlane.assessment.domain.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Inserts a new user (registration). Use the targeted updates below for existing accounts. */
    User save(User user);

    /**
     * Writes only username and e-mail, so a password change committed meanwhile is never reverted.
     *
     * @throws com.ballastlane.assessment.domain.exception.UserNotFoundException when the account is gone
     * @throws com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException on a unique violation
     * @throws com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException on a unique violation
     */
    User updateProfile(Long userId, String username, String email);

    /**
     * Writes only the password hash, so a concurrent profile edit is never reverted.
     *
     * @throws com.ballastlane.assessment.domain.exception.UserNotFoundException when the account is gone
     */
    void updatePasswordHash(Long userId, String passwordHash);
}
