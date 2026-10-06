package com.ballastlane.assessment.infrastructure.persistence;

import com.ballastlane.assessment.domain.User;
import org.springframework.stereotype.Component;

@Component
class UserPersistenceMapper {

    UserEntity toEntity(User user) {
        return new UserEntity(user.id(), user.username(), user.email(), user.passwordHash(), user.role());
    }

    User toDomain(UserEntity entity) {
        return new User(entity.getId(), entity.getUsername(), entity.getEmail(), entity.getPasswordHash(), entity.getRole());
    }
}
