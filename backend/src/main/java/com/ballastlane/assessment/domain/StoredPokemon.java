package com.ballastlane.assessment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One entry of a trainer's collection: the shared Pokemon data (PokeAPI-owned, refreshed by sync) plus that trainer's
 * own {@link LocalAttributes}. {@code id} and {@code version} belong to the entry, not to the shared Pokemon, and are
 * null until first saved; the version guards the trainer's notes against stale writes. {@code trainerId} is the trainer
 * whose collection this entry is in.
 */
public record StoredPokemon(
        Long id,
        Long version,
        Long trainerId,
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

    public static StoredPokemon fromDetail(PokemonDetail detail, Instant now, Actor trainer) {
        return copyOf(detail, null, null, trainer.userId(), LocalAttributes.empty(), now);
    }

    // Species data comes from separate upstream calls that can come back empty; a partial answer must not erase
    // what an earlier, complete sync already stored.
    public StoredPokemon refreshedFrom(PokemonDetail detail, Instant now) {
        StoredPokemon fresh = copyOf(detail, id, version, trainerId, localAttributes, now);
        return new StoredPokemon(fresh.id, fresh.version, fresh.trainerId, fresh.pokeApiId, fresh.name, fresh.spriteUrl, fresh.imageUrl,
                fresh.types, fresh.abilities, fresh.stats, fresh.weightKg, fresh.heightM,
                fresh.genus != null ? fresh.genus : genus,
                fresh.description != null ? fresh.description : description,
                fresh.evolutionChain != null ? fresh.evolutionChain : evolutionChain,
                fresh.localAttributes, fresh.syncedAt);
    }

    public StoredPokemon withLocalAttributes(LocalAttributes attributes) {
        return new StoredPokemon(id, version, trainerId, pokeApiId, name, spriteUrl, imageUrl, types, abilities, stats, weightKg,
                heightM, genus, description, evolutionChain, attributes, syncedAt);
    }

    private static StoredPokemon copyOf(
            PokemonDetail detail, Long id, Long version, Long trainerId, LocalAttributes localAttributes, Instant syncedAt) {
        return new StoredPokemon(id, version, trainerId, detail.id(), detail.name(), detail.spriteUrl(), detail.imageUrl(), detail.types(),
                detail.abilities(), detail.stats(), detail.weightKg(), detail.heightM(), detail.genus(), detail.description(),
                detail.evolutionChain(), localAttributes, syncedAt);
    }
}
