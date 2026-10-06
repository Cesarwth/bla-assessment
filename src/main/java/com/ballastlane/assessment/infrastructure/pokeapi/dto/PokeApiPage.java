package com.ballastlane.assessment.infrastructure.pokeapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PokeApiPage(int count, List<Entry> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String name, String url) {
    }
}
