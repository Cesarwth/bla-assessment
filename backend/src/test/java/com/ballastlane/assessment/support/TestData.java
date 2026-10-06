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
    /** Owner of every {@link #stored} record unless a test says otherwise. */
    public static final long OWNER_ID = 1L;

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
        return storedOwnedBy(OWNER_ID, dbId, version, pokeApiId, name, local);
    }

    /** {@code ownerId} may be null to model a record nobody owns (legacy or seeded data). */
    public static StoredPokemon storedOwnedBy(
            Long ownerId, long dbId, long version, int pokeApiId, String name, LocalAttributes local) {
        PokemonDetail d = detail(pokeApiId, name);
        return new StoredPokemon(dbId, version, ownerId, d.id(), d.name(), d.spriteUrl(), d.imageUrl(), d.types(),
                d.abilities(), d.stats(), d.weightKg(), d.heightM(), d.genus(), d.description(), d.evolutionChain(),
                local, NOW);
    }
}
