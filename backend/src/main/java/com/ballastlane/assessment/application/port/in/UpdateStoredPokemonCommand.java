package com.ballastlane.assessment.application.port.in;

import com.ballastlane.assessment.domain.Actor;
import java.util.List;

/**
 * Null text/tags mean "leave unchanged"; empty text or an empty list means "clear". {@code version} is the one the
 * client last read: the update is refused if the record has changed since.
 */
public record UpdateStoredPokemonCommand(
        int pokeApiId, Long version, String nickname, String region, List<String> tags, Actor actor) {
}
