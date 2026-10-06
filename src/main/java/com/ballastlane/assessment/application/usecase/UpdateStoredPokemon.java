package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonCommand;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.PokemonIdentifier;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.exception.InvalidStoredPokemonUpdateException;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;

public class UpdateStoredPokemon implements UpdateStoredPokemonUseCase {

    private final PokemonRepository repository;

    public UpdateStoredPokemon(PokemonRepository repository) {
        this.repository = repository;
    }

    @Override
    public StoredPokemon update(UpdateStoredPokemonCommand command) {
        int id = PokemonIdentifier.requirePositive(command.pokeApiId());
        if (command.nickname() == null && command.region() == null && command.tags() == null) {
            throw new InvalidStoredPokemonUpdateException("Provide at least one of nickname, region or tags");
        }
        StoredPokemon current = repository.findByPokeApiId(id).orElseThrow(() -> new StoredPokemonNotFoundException(id));
        LocalAttributes now = current.localAttributes();
        // LocalAttributes validates and normalizes, so the same rules apply to every way of building one.
        LocalAttributes merged = new LocalAttributes(
                command.nickname() != null ? command.nickname() : now.nickname(),
                command.region() != null ? command.region() : now.region(),
                command.tags() != null ? command.tags() : now.tags());
        return repository.save(current.withLocalAttributes(merged));
    }
}
