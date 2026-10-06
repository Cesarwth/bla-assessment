package com.ballastlane.assessment.infrastructure.config;

import com.ballastlane.assessment.domain.PokemonDetail;
import java.util.List;

/** One entry of a demo trainer's collection: the Pokémon's PokeAPI data plus that trainer's notes (the account is {@code owner}). */
public record DemoEntry(String owner, String nickname, String region, List<String> tags, PokemonDetail detail) {
}
