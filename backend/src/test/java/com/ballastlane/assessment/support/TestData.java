package com.ballastlane.assessment.support;

import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.EvolutionNode;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonStat;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.StoredPokemon;
import java.time.Instant;
import java.util.List;

/** Small builders shared by tests so each test only states what it cares about. */
public final class TestData {

    public static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    /** Trainer of every {@link #stored} record unless a test says otherwise. */
    public static final long TRAINER_ID = 1L;

    private TestData() {
    }

    public static Actor user(long id) {
        return new Actor(id, "user" + id, Role.USER);
    }

    public static Actor admin(long id) {
        return new Actor(id, "admin" + id, Role.ADMIN);
    }

    public static PokemonDetail detail(int id, String name) {
        return new PokemonDetail(id, name, "img/" + id, "sprite/" + id, List.of("electric"), 6.0, 0.4,
                List.of("static"), List.of(new PokemonStat("hp", 35)), "Mouse Pokémon", "description of " + name,
                new EvolutionNode(id, name, "sprite/" + id, List.of(), List.of()));
    }

    public static StoredPokemon stored(long dbId, long version, int pokeApiId, String name, LocalAttributes local) {
        return storedFor(TRAINER_ID, dbId, version, pokeApiId, name, local);
    }

    /** {@code trainerId} is the trainer whose collection the record is in. */
    public static StoredPokemon storedFor(
            Long trainerId, long dbId, long version, int pokeApiId, String name, LocalAttributes local) {
        PokemonDetail detail = detail(pokeApiId, name);
        return new StoredPokemon(dbId, version, trainerId, detail.id(), detail.name(), detail.spriteUrl(), detail.imageUrl(),
                detail.types(), detail.abilities(), detail.stats(), detail.weightKg(), detail.heightM(), detail.genus(),
                detail.description(), detail.evolutionChain(), local, NOW);
    }
}
