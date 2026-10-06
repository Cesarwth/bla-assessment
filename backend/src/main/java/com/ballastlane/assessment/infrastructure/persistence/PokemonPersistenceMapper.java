package com.ballastlane.assessment.infrastructure.persistence;

import com.ballastlane.assessment.domain.EvolutionNode;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.PokemonStat;
import com.ballastlane.assessment.domain.StoredPokemon;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class PokemonPersistenceMapper {

    // Own mapper on purpose: the stored format must not change if web serialization settings do.
    // SHORTCUT: the evolution chain is one JSON column; upgrade to a table when we need to query by evolution.
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A new catalog row from the PokeAPI-owned part of an entry. */
    PokemonEntity toCatalogEntity(StoredPokemon source) {
        PokemonEntity entity = new PokemonEntity();
        entity.pokeApiId = source.pokeApiId();
        refreshCatalog(entity, source);
        return entity;
    }

    /**
     * Copies the PokeAPI-owned fields onto the shared row. Genus, description and evolution chain come from separate
     * upstream calls that can come back empty: a partial answer must not erase what an earlier sync (maybe by another
     * trainer) already stored.
     */
    void refreshCatalog(PokemonEntity entity, StoredPokemon source) {
        entity.name = source.name();
        entity.spriteUrl = source.spriteUrl();
        entity.imageUrl = source.imageUrl();
        entity.types.clear();
        entity.types.addAll(source.types());
        entity.abilities.clear();
        entity.abilities.addAll(source.abilities());
        entity.stats.clear();
        entity.stats.addAll(source.stats().stream().map(s -> new StatEmbeddable(s.name(), s.baseValue())).toList());
        entity.weightKg = source.weightKg();
        entity.heightM = source.heightM();
        if (source.genus() != null) {
            entity.genus = source.genus();
        }
        if (source.description() != null) {
            entity.description = source.description();
        }
        if (source.evolutionChain() != null) {
            entity.evolutionChainJson = toJson(source.evolutionChain());
        }
        entity.syncedAt = source.syncedAt();
    }

    TrainerPokemonEntity toNewEntry(StoredPokemon source, PokemonEntity catalog) {
        TrainerPokemonEntity entry = new TrainerPokemonEntity();
        entry.userId = source.ownerId();
        entry.pokemon = catalog;
        entry.addedAt = source.syncedAt();
        applyNotes(entry, source.localAttributes());
        return entry;
    }

    void applyNotes(TrainerPokemonEntity entry, LocalAttributes notes) {
        entry.nickname = notes.nickname();
        entry.region = notes.region();
        entry.tags.clear();
        entry.tags.addAll(notes.tags());
    }

    StoredPokemon toDomain(TrainerPokemonEntity entry) {
        PokemonEntity entity = entry.pokemon;
        return new StoredPokemon(
                entry.id,
                entry.version,
                entry.userId,
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
                new LocalAttributes(entry.nickname, entry.region, entry.tags),
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
