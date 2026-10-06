package com.ballastlane.assessment.infrastructure.pokeapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PokeApiPokemon(
        int id,
        String name,
        int height,
        int weight,
        List<TypeSlot> types,
        List<AbilitySlot> abilities,
        List<StatSlot> stats,
        Sprites sprites,
        NamedRef species) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TypeSlot(NamedRef type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AbilitySlot(NamedRef ability) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatSlot(@JsonProperty("base_stat") int baseStat, NamedRef stat) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sprites(@JsonProperty("front_default") String frontDefault, Other other) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Other(@JsonProperty("official-artwork") Artwork officialArtwork) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artwork(@JsonProperty("front_default") String frontDefault) {
    }
}
