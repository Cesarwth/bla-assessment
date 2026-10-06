package com.ballastlane.assessment.infrastructure.persistence;

import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.User;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository repository;
    private final UserPersistenceMapper mapper;

    JpaUserRepositoryAdapter(SpringDataUserRepository repository, UserPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
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
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(user)));
    }
}
