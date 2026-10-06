package com.ballastlane.assessment.infrastructure.pokeapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PokeApiEvolutionChain(ChainLink chain) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChainLink(
            NamedRef species,
            @JsonProperty("evolution_details") List<EvolutionDetail> evolutionDetails,
            @JsonProperty("evolves_to") List<ChainLink> evolvesTo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvolutionDetail(
            NamedRef trigger,
            NamedRef item,
            @JsonProperty("min_level") Integer minLevel,
            @JsonProperty("min_happiness") Integer minHappiness,
            @JsonProperty("time_of_day") String timeOfDay) {
    }
}
