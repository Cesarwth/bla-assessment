package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.UserNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProfileTest {

    private static final Actor ASH = new Actor(1L, "ash", Role.USER);

    @Mock UserRepository users;

    @Test
    void returnsTheCurrentStateOfTheActorsAccount() {
        User stored = new User(1L, "ash", "ash@pallet.town", "hashed", Role.USER);
        when(users.findById(1L)).thenReturn(Optional.of(stored));

        assertThat(new GetProfile(users).get(ASH)).isEqualTo(stored);
    }

    @Test
    void failsWhenTheAccountNoLongerExists() {
        when(users.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetProfile(users).get(ASH)).isInstanceOf(UserNotFoundException.class);
    }
}
