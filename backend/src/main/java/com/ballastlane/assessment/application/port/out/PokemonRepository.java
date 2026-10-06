package com.ballastlane.assessment.application.port.out;

import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.StoredPokemon;
import java.util.List;
import java.util.Optional;

/** Every operation is scoped to one trainer's collection; the Pokemon data itself is a catalog shared by all. */
public interface PokemonRepository {
    Optional<StoredPokemon> findByTrainerAndPokeApiId(long trainerId, int pokeApiId);

    PageResult<StoredPokemon> findPage(long trainerId, int page, int size);

    /** Only the PokeAPI ids of the trainer's entries, ascending. */
    List<Integer> findPokeApiIds(long trainerId);

    /**
     * Refreshes the shared Pokemon data (unless the stored copy is newer) and creates or updates the trainer's entry.
     * Notes are only written together with a matching entry version.
     */
    StoredPokemon save(StoredPokemon entry);

    /**
     * Writes only the trainer's own notes of an existing entry, guarded by the entry version. The shared Pokemon row is
     * never touched, so two trainers editing notes of the same Pokemon cannot collide.
     *
     * @throws com.ballastlane.assessment.domain.exception.StaleStoredPokemonException when the version is outdated or
     *         the entry no longer exists
     */
    StoredPokemon updateNotes(StoredPokemon entry);

    /**
     * Removes the trainer's entry only; the shared Pokemon stays for everybody else.
     *
     * @return true when an entry was removed, false when the trainer did not have it
     */
    boolean deleteByTrainerAndPokeApiId(long trainerId, int pokeApiId);
}
