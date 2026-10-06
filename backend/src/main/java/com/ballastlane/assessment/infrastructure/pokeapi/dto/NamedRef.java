package com.ballastlane.assessment.infrastructure.pokeapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** PokeAPI's ubiquitous {name, url} reference. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NamedRef(String name, String url) {
}
