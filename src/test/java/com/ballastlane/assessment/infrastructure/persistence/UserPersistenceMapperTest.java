package com.ballastlane.assessment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import org.junit.jupiter.api.Test;

class UserPersistenceMapperTest {

    private final UserPersistenceMapper mapper = new UserPersistenceMapper();

    @Test
    void roundTripKeepsEveryField() {
        User user = new User(7L, "ash", "ash@pallet.town", "hashed", Role.ADMIN);

        assertThat(mapper.toDomain(mapper.toEntity(user))).isEqualTo(user);
    }
}
