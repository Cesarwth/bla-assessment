package com.ballastlane.assessment.infrastructure.pokeapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PokeApiSpecies(
        List<Genus> genera,
        @JsonProperty("flavor_text_entries") List<FlavorText> flavorTextEntries,
        @JsonProperty("evolution_chain") UrlRef evolutionChain) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genus(String genus, NamedRef language) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FlavorText(@JsonProperty("flavor_text") String flavorText, NamedRef language) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UrlRef(String url) {
    }
}
