package com.ballastlane.assessment.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPokemonRepository extends JpaRepository<PokemonEntity, Long> {
    Optional<PokemonEntity> findByPokeApiId(int pokeApiId);
}
