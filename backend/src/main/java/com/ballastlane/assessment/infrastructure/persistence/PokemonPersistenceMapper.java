package com.ballastlane.assessment.infrastructure.persistence;

import com.ballastlane.assessment.domain.EvolutionNode;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.PokemonStat;
import com.ballastlane.assessment.domain.StoredPokemon;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class PokemonPersistenceMapper {

    // Own mapper on purpose: the stored format must not change if web serialization settings do.
    // SHORTCUT: the evolution chain is one JSON column; upgrade to a table when we need to query by evolution.
    private static final JsonMapper JSON = JsonMapper.builder().build();

    PokemonEntity toEntity(StoredPokemon pokemon) {
        PokemonEntity entity = new PokemonEntity();
        entity.id = pokemon.id();
        entity.version = pokemon.version();
        entity.ownerId = pokemon.ownerId();
        entity.pokeApiId = pokemon.pokeApiId();
        entity.name = pokemon.name();
        entity.spriteUrl = pokemon.spriteUrl();
        entity.imageUrl = pokemon.imageUrl();
        entity.types = new ArrayList<>(pokemon.types());
        entity.abilities = new ArrayList<>(pokemon.abilities());
        entity.stats = new ArrayList<>(pokemon.stats().stream().map(s -> new StatEmbeddable(s.name(), s.baseValue())).toList());
        entity.weightKg = pokemon.weightKg();
        entity.heightM = pokemon.heightM();
        entity.genus = pokemon.genus();
        entity.description = pokemon.description();
        entity.evolutionChainJson = toJson(pokemon.evolutionChain());
        entity.nickname = pokemon.localAttributes().nickname();
        entity.region = pokemon.localAttributes().region();
        entity.tags = new ArrayList<>(pokemon.localAttributes().tags());
        entity.syncedAt = pokemon.syncedAt();
        return entity;
    }

    StoredPokemon toDomain(PokemonEntity entity) {
        return new StoredPokemon(
                entity.id,
                entity.version,
                entity.ownerId,
                entity.pokeApiId,
                entity.name,
                entity.spriteUrl,
                entity.imageUrl,
                entity.types,
                entity.abilities,
                entity.stats.stream().map(s -> new PokemonStat(s.name, s.baseValue)).toList(),
                entity.weightKg,
                entity.heightM,
                entity.genus,
                entity.description,
                fromJson(entity.evolutionChainJson),
                new LocalAttributes(entity.nickname, entity.region, entity.tags),
                entity.syncedAt);
    }

    private static String toJson(EvolutionNode chain) {
        if (chain == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(chain);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize the evolution chain", e);
        }
    }

    private static EvolutionNode fromJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return JSON.readValue(json, EvolutionNode.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored evolution chain is not readable", e);
        }
    }
}
