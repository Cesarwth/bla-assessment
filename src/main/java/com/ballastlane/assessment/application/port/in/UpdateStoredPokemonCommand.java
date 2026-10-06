package com.ballastlane.assessment.application.port.in;

import java.util.List;

/** Null means "leave unchanged"; empty text or an empty list means "clear". */
public record UpdateStoredPokemonCommand(int pokeApiId, String nickname, String region, List<String> tags) {
}
