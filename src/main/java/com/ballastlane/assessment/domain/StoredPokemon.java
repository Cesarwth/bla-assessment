package com.ballastlane.assessment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Local replica of a Pokemon. PokeAPI-owned fields are refreshed by sync; {@link LocalAttributes} are ours.
 * {@code id} and {@code version} are null until first saved; the version travels with the aggregate so a stale
 * write can be detected.
 */
public record StoredPokemon(
        Long id,
        Long version,
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

    public static StoredPokemon fromDetail(PokemonDetail detail, Instant now) {
        return copyOf(detail, null, null, LocalAttributes.empty(), now);
    }

    public StoredPokemon refreshedFrom(PokemonDetail detail, Instant now) {
        return copyOf(detail, id, version, localAttributes, now);
    }

    public StoredPokemon withLocalAttributes(LocalAttributes attributes) {
        return new StoredPokemon(id, version, pokeApiId, name, spriteUrl, imageUrl, types, abilities, stats, weightKg,
                heightM, genus, description, evolutionChain, attributes, syncedAt);
    }

    private static StoredPokemon copyOf(
            PokemonDetail d, Long id, Long version, LocalAttributes local, Instant syncedAt) {
        return new StoredPokemon(id, version, d.id(), d.name(), d.spriteUrl(), d.imageUrl(), d.types(), d.abilities(),
                d.stats(), d.weightKg(), d.heightM(), d.genus(), d.description(), d.evolutionChain(), local, syncedAt);
    }
}
