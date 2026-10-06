package com.ballastlane.assessment.application.port.out;

import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.StoredPokemon;
import java.util.Optional;

public interface PokemonRepository {
    Optional<StoredPokemon> findByPokeApiId(int pokeApiId);

    PageResult<StoredPokemon> findPage(int page, int size);

    StoredPokemon save(StoredPokemon pokemon);

    /** @return true when a row was removed, false when there was nothing to delete */
    boolean deleteByPokeApiId(int pokeApiId);
}
