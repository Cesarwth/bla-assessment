package com.ballastlane.assessment.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Absent or null = leave unchanged; empty string / empty list = clear. Limits are enforced by the domain.
 * {@code version} is the one from the last read of the record; stale versions are refused with 409.
 */
public record UpdatePokemonRequest(@NotNull Long version, String nickname, String region, List<String> tags) {
}
