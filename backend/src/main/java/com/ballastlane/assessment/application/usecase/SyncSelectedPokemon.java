package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.SyncSelectedPokemonUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.SyncOutcome;
import com.ballastlane.assessment.domain.SyncReport;
import com.ballastlane.assessment.domain.SyncResult;
import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.domain.exception.DuplicateStoredPokemonException;
import com.ballastlane.assessment.domain.exception.InvalidSyncSelectionException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SyncSelectedPokemon implements SyncSelectedPokemonUseCase {

    // Same as the largest catalog page, so "select a whole page and add it" always fits in one request.
    public static final int MAX_SELECTION = 50;

    private final SyncPokemonUseCase syncOne;

    private final Clock clock;
    private final Duration maxDuration;

    public SyncSelectedPokemon(SyncPokemonUseCase syncOne, Clock clock, Duration maxDuration) {
        this.syncOne = syncOne;
        this.clock = clock;
        this.maxDuration = maxDuration;
    }

    @Override
    public SyncReport sync(List<Integer> ids, Actor actor) {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidSyncSelectionException("Select at least one Pokemon to sync");
        }
        if (ids.size() > MAX_SELECTION) {
            throw new InvalidSyncSelectionException("Select at most " + MAX_SELECTION + " Pokemon per sync");
        }
        if (ids.stream().anyMatch(id -> id == null || id < 1)) {
            throw new InvalidSyncSelectionException("Pokemon ids must be positive integers");
        }
        // A repeated id would hit upstream twice for nothing; first occurrence keeps the user's order.
        Set<Integer> distinct = new LinkedHashSet<>(ids);
        int created = 0;
        int updated = 0;
        List<SyncReport.Failure> failures = new ArrayList<>();
        String abortedReason = null;
        // Up to 50 ids x 3 upstream calls each: without a deadline the request outlives the proxy's timeout while the
        // server keeps working. Stopping early returns what was saved so far and the client retries the rest.
        Instant deadline = clock.instant().plus(maxDuration);
        for (int id : distinct) {
            if (!clock.instant().isBefore(deadline) && created + updated + failures.size() > 0) {
                abortedReason = "Time budget exceeded before id " + id;
                break;
            }
            try {
                SyncResult syncResult = syncOne.sync(String.valueOf(id), actor);
                if (syncResult.outcome() == SyncOutcome.CREATED) {
                    created++;
                } else {
                    updated++;
                }
            } catch (PokemonNotFoundException exception) {
                // PokeAPI has gaps (e.g. 1026-10000): a missing id is a per-item failure, not a reason to stop.
                failures.add(new SyncReport.Failure(String.valueOf(id), "Not found upstream"));
            } catch (StaleStoredPokemonException exception) {
                // Someone edited this record while we synced it: report it and keep the rest of the selection.
                failures.add(new SyncReport.Failure(String.valueOf(id), "Modified concurrently, retry it"));
            } catch (DuplicateStoredPokemonException exception) {
                // Still racing after SyncPokemon's own retries (e.g. the same trainer adding it from two tabs).
                failures.add(new SyncReport.Failure(String.valueOf(id), "Already being added, retry it"));
            } catch (CatalogUnavailableException exception) {
                // Upstream is down: stop instead of repeating the failure for every remaining id. What was already
                // saved is reported; if nothing was processed yet there is no report to give, so the error propagates.
                if (created + updated == 0 && failures.isEmpty()) {
                    throw exception;
                }
                abortedReason = "Catalog unavailable at id " + id;
                break;
            }
        }
        return new SyncReport(created, updated, failures, abortedReason);
    }
}
