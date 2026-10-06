package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.ListStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.StoredPokemon;

public class ListStoredPokemon implements ListStoredPokemonUseCase {

    private final PokemonRepository repository;

    public ListStoredPokemon(PokemonRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<StoredPokemon> list(Integer page, Integer size, Actor actor) {
        Pagination pagination = Pagination.of(page, size);
        return repository.findPage(actor.userId(), pagination.page(), pagination.size());
    }
}
