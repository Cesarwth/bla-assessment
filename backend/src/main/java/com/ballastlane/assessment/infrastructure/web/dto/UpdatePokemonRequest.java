package com.ballastlane.assessment.infrastructure.web.dto;

import java.util.List;

/** Absent or null = leave unchanged; empty string / empty list = clear. Limits are enforced by the domain. */
public record UpdatePokemonRequest(String nickname, String region, List<String> tags) {
}
