package com.ballastlane.assessment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Local replica of a Pokemon. PokeAPI-owned fields are refreshed by sync; {@link LocalAttributes} are ours.
 * {@code id} and {@code version} are null until first saved; the version travels with the aggregate so a stale
 * write can be detected. {@code ownerId} is the user who first synced the record (null for ownerless legacy rows).
 */
public record StoredPokemon(
        Long id,
        Long version,
        Long ownerId,
        int pokeApiId,
        String name,
        String spriteUrl,
        String imageUrl,
        List<String> types,
        List<String> abilities,
        List<PokemonStat> stats,
        double weightKg,
        double heightM,
        String genus,
        String description,
        EvolutionNode evolutionChain,
        LocalAttributes localAttributes,
        Instant syncedAt) {

    public StoredPokemon {
        types = List.copyOf(types);
        abilities = List.copyOf(abilities);
        stats = List.copyOf(stats);
        Objects.requireNonNull(localAttributes, "localAttributes");
    }

    public static StoredPokemon fromDetail(PokemonDetail detail, Instant now, Actor owner) {
        return copyOf(detail, null, null, owner.userId(), LocalAttributes.empty(), now);
    }

    /** The owner or any admin; a record nobody owns can only be edited by an admin. */
    public boolean canBeEditedBy(Actor actor) {
        return actor.isAdmin() || (ownerId != null && ownerId.equals(actor.userId()));
    }

    // Species data comes from separate upstream calls that can come back empty; a partial answer must not erase
    // what an earlier, complete sync already stored.
    public StoredPokemon refreshedFrom(PokemonDetail detail, Instant now) {
        StoredPokemon fresh = copyOf(detail, id, version, ownerId, localAttributes, now);
        return new StoredPokemon(fresh.id, fresh.version, fresh.ownerId, fresh.pokeApiId, fresh.name, fresh.spriteUrl, fresh.imageUrl,
                fresh.types, fresh.abilities, fresh.stats, fresh.weightKg, fresh.heightM,
                fresh.genus != null ? fresh.genus : genus,
                fresh.description != null ? fresh.description : description,
                fresh.evolutionChain != null ? fresh.evolutionChain : evolutionChain,
                fresh.localAttributes, fresh.syncedAt);
    }

    public StoredPokemon withLocalAttributes(LocalAttributes attributes) {
        return new StoredPokemon(id, version, ownerId, pokeApiId, name, spriteUrl, imageUrl, types, abilities, stats, weightKg,
                heightM, genus, description, evolutionChain, attributes, syncedAt);
    }

    private static StoredPokemon copyOf(
            PokemonDetail d, Long id, Long version, Long ownerId, LocalAttributes local, Instant syncedAt) {
        return new StoredPokemon(id, version, ownerId, d.id(), d.name(), d.spriteUrl(), d.imageUrl(), d.types(), d.abilities(),
                d.stats(), d.weightKg(), d.heightM(), d.genus(), d.description(), d.evolutionChain(), local, syncedAt);
    }
}
