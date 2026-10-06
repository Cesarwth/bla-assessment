package com.ballastlane.assessment.application.usecase;

import com.ballastlane.assessment.application.port.in.SyncPokemonRangeUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.SyncOutcome;
import com.ballastlane.assessment.domain.SyncReport;
import com.ballastlane.assessment.domain.SyncResult;
import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.domain.exception.InvalidSyncRangeException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import java.util.ArrayList;
import java.util.List;

public class SyncPokemonRange implements SyncPokemonRangeUseCase {

    // Each id costs up to 3 upstream calls; the cap keeps one request from hammering PokeAPI.
    public static final int MAX_RANGE = 20;

    private final SyncPokemonUseCase syncOne;

    public SyncPokemonRange(SyncPokemonUseCase syncOne) {
        this.syncOne = syncOne;
    }

    @Override
    public SyncReport sync(int from, int to, Actor actor) {
        if (from < 1 || to < from || to - from + 1 > MAX_RANGE) {
            throw new InvalidSyncRangeException(
                    "Range must satisfy 1 <= from <= to and contain at most " + MAX_RANGE + " ids");
        }
        int created = 0;
        int updated = 0;
        List<SyncReport.Failure> failures = new ArrayList<>();
        String abortedReason = null;
        for (int id = from; id <= to; id++) {
            try {
                SyncResult result = syncOne.sync(String.valueOf(id), actor);
                if (result.outcome() == SyncOutcome.CREATED) {
                    created++;
                } else {
                    updated++;
                }
            } catch (PokemonNotFoundException e) {
                // PokeAPI has gaps (e.g. 1026-10000): a missing id is a per-item failure, not a reason to stop.
                failures.add(new SyncReport.Failure(String.valueOf(id), "Not found upstream"));
            } catch (StaleStoredPokemonException e) {
                // Someone edited this record while we synced it: report it and keep the rest of the range.
                failures.add(new SyncReport.Failure(String.valueOf(id), "Modified concurrently, retry it"));
            } catch (CatalogUnavailableException e) {
                // Upstream is down: stop instead of repeating the failure for every remaining id. What was already
                // saved is reported; if nothing was processed yet there is no report to give, so the error propagates.
                if (created + updated == 0 && failures.isEmpty()) {
                    throw e;
                }
                abortedReason = "Catalog unavailable at id " + id;
                break;
            }
        }
        return new SyncReport(created, updated, failures, abortedReason);
    }
}
