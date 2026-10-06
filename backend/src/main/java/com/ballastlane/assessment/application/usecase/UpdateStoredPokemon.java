package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonCommand;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.PokemonIdentifier;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.exception.InvalidStoredPokemonUpdateException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;
import java.util.Objects;

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
        if (command.version() == null) {
            throw new InvalidStoredPokemonUpdateException("The version of the record you are editing is required");
        }
        StoredPokemon current = repository.findByTrainerAndPokeApiId(command.actor().userId(), id).orElseThrow(() -> new StoredPokemonNotFoundException(id));
        // Another trainer's entry is simply "not found" above: its existence is never revealed.
        if (!Objects.equals(command.version(), current.version())) {
            throw new StaleStoredPokemonException(id);
        }
        LocalAttributes now = current.localAttributes();
        // LocalAttributes validates and normalizes, so the same rules apply to every way of building one.
        LocalAttributes merged = new LocalAttributes(
                command.nickname() != null ? command.nickname() : now.nickname(),
                command.region() != null ? command.region() : now.region(),
                command.tags() != null ? command.tags() : now.tags());
        return repository.updateNotes(current.withLocalAttributes(merged));
    }
}
