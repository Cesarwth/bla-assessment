package com.ballastlane.assessment.infrastructure.persistence;

import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.UserNotFoundException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository repository;
    private final UserPersistenceMapper mapper;

    JpaUserRepositoryAdapter(SpringDataUserRepository repository, UserPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return repository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    // saveAndFlush so unique-constraint violations surface here, not at some later commit.
    @Override
    public User save(User user) {
        try {
            return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(user)));
        } catch (DataIntegrityViolationException e) {
            // The use case checks first, but two simultaneous registrations can both pass that check.
            throw asDomainConflict(e, user.username(), user.email());
        }
    }

    @Override
    @Transactional
    public User updateProfile(Long userId, String username, String email) {
        int updated;
        try {
            updated = repository.updateProfile(userId, username, email);
        } catch (DataIntegrityViolationException e) {
            throw asDomainConflict(e, username, email);
        }
        if (updated == 0) {
            throw new UserNotFoundException();
        }
        return findById(userId).orElseThrow(UserNotFoundException::new);
    }

    @Override
    @Transactional
    public void updatePasswordHash(Long userId, String passwordHash) {
        if (repository.updatePasswordHash(userId, passwordHash) == 0) {
            throw new UserNotFoundException();
        }
    }

    private static RuntimeException asDomainConflict(DataIntegrityViolationException e, String username, String email) {
        if (UniqueViolations.isUnique(e)) {
            String details = UniqueViolations.details(e);
            if (details.contains("uq_users_username")) {
                return new UsernameAlreadyExistsException(username);
            }
            if (details.contains("uq_users_email")) {
                return new EmailAlreadyExistsException(email);
            }
        }
        return e;
    }
}
