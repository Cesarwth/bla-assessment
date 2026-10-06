package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.GetProfileUseCase;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.UserNotFoundException;

public class GetProfile implements GetProfileUseCase {

    private final UserRepository users;

    public GetProfile(UserRepository users) {
        this.users = users;
    }

    @Override
    public User get(Actor actor) {
        // Read from storage, not from the token: after a rename the old token still carries the old username.
        return users.findById(actor.userId()).orElseThrow(UserNotFoundException::new);
    }
}
