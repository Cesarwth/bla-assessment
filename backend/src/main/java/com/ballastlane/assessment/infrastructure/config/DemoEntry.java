package com.ballastlane.assessment.infrastructure.config;

import com.ballastlane.assessment.domain.PokemonDetail;
import java.util.List;

/** One Pokémon of the demo collection: its PokeAPI data plus the local fields and the account that owns it. */
public record DemoEntry(String owner, String nickname, String region, List<String> tags, PokemonDetail detail) {
}
