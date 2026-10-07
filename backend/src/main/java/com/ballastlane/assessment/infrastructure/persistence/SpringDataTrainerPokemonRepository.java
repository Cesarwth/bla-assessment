package com.ballastlane.assessment.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTrainerPokemonRepository extends JpaRepository<TrainerPokemonEntity, Long> {

    // The Pokemon is fetched with the page: mapping needs it for every row and it would otherwise be one query each.
    @EntityGraph(attributePaths = "pokemon")
    Page<TrainerPokemonEntity> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "pokemon")
    Optional<TrainerPokemonEntity> findByUserIdAndPokemonPokeApiId(Long userId, int pokeApiId);

    // Scalar projection: no entities, so no lazy collections are touched.
    @Query("select trainerPokemon.pokemon.pokeApiId from TrainerPokemonEntity trainerPokemon where trainerPokemon.userId = :userId order by trainerPokemon.pokemon.pokeApiId")
    List<Integer> findPokeApiIdsByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);

    // Derived delete loads the entity first so JPA also removes its tags.
    long deleteByUserIdAndPokemonPokeApiId(Long userId, int pokeApiId);
}
