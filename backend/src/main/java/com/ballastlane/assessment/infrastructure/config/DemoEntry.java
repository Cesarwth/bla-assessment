package com.ballastlane.assessment.infrastructure.config;

import com.ballastlane.assessment.domain.PokemonDetail;
import java.util.List;

/** One entry of a demo trainer's collection: the Pokémon's PokeAPI data plus that trainer's notes (the account is {@code trainer}). */
public record DemoEntry(String trainer, String nickname, String region, List<String> tags, PokemonDetail detail) {
}
