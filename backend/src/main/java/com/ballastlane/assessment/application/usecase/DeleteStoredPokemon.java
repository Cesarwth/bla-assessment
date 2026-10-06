package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.DeleteStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.PokemonIdentifier;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;

public class DeleteStoredPokemon implements DeleteStoredPokemonUseCase {

    private final PokemonRepository repository;

    public DeleteStoredPokemon(PokemonRepository repository) {
        this.repository = repository;
    }

    @Override
    public void delete(int pokeApiId) {
        int id = PokemonIdentifier.requirePositive(pokeApiId);
        if (!repository.deleteByPokeApiId(id)) {
            throw new StoredPokemonNotFoundException(id);
        }
    }
}
