package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.GetStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.PokemonIdentifier;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;

public class GetStoredPokemon implements GetStoredPokemonUseCase {

    private final PokemonRepository repository;

    public GetStoredPokemon(PokemonRepository repository) {
        this.repository = repository;
    }

    @Override
    public StoredPokemon get(int pokeApiId, Actor actor) {
        int id = PokemonIdentifier.requirePositive(pokeApiId);
        return repository.findByTrainerAndPokeApiId(actor.userId(), id).orElseThrow(() -> new StoredPokemonNotFoundException(id));
    }
}
