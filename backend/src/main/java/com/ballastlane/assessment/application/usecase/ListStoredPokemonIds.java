package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.ListStoredPokemonIdsUseCase;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.Actor;
import java.util.List;

public class ListStoredPokemonIds implements ListStoredPokemonIdsUseCase {

    private final PokemonRepository repository;

    public ListStoredPokemonIds(PokemonRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Integer> list(Actor actor) {
        return repository.findPokeApiIds(actor.userId());
    }
}
