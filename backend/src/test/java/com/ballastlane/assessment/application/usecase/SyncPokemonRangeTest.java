package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.SyncOutcome;
import com.ballastlane.assessment.domain.SyncReport;
import com.ballastlane.assessment.domain.SyncResult;
import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.domain.exception.InvalidSyncRangeException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import com.ballastlane.assessment.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncPokemonRangeTest {

    private static final Actor ACTOR = TestData.user(5);

    @Mock SyncPokemonUseCase syncOne;
    SyncPokemonRange syncRange;

    @BeforeEach
    void setUp() {
        syncRange = new SyncPokemonRange(syncOne);
    }

    private static SyncResult result(SyncOutcome outcome, int id) {
        return new SyncResult(outcome, TestData.stored(id, 0, id, "p" + id, LocalAttributes.empty()));
    }

    @Test
    void syncsEveryIdInTheRangeAndCountsOutcomes() {
        when(syncOne.sync("1", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 1));
        when(syncOne.sync("2", ACTOR)).thenReturn(result(SyncOutcome.UPDATED, 2));
        when(syncOne.sync("3", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 3));

        SyncReport report = syncRange.sync(1, 3, ACTOR);

        assertThat(report.created()).isEqualTo(2);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void everyIdIsSyncedOnBehalfOfTheSameActor() {
        Actor other = TestData.user(9);
        when(syncOne.sync("1", other)).thenReturn(result(SyncOutcome.CREATED, 1));
        when(syncOne.sync("2", other)).thenReturn(result(SyncOutcome.CREATED, 2));

        assertThat(syncRange.sync(1, 2, other).created()).isEqualTo(2);
    }

    @Test
    void reportsIdsMissingUpstreamAsFailuresAndKeepsGoing() {
        when(syncOne.sync("1025", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 1025));
        when(syncOne.sync("1026", ACTOR)).thenThrow(new PokemonNotFoundException("1026"));
        when(syncOne.sync("1027", ACTOR)).thenThrow(new PokemonNotFoundException("1027"));

        SyncReport report = syncRange.sync(1025, 1027, ACTOR);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.failures()).extracting(SyncReport.Failure::identifier).containsExactly("1026", "1027");
        assertThat(report.failures().get(0).reason()).isEqualTo("Not found upstream");
    }

    @Test
    void returnsAPartialReportWhenTheCatalogGoesDownMidway() {
        when(syncOne.sync("1", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 1));
        when(syncOne.sync("2", ACTOR)).thenThrow(new CatalogUnavailableException("down", false, null));

        SyncReport report = syncRange.sync(1, 5, ACTOR);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.abortedReason()).contains("2");
        verify(syncOne, never()).sync("3", ACTOR);
    }

    @Test
    void rethrowsWhenTheCatalogIsDownBeforeAnythingWasProcessed() {
        when(syncOne.sync("1", ACTOR)).thenThrow(new CatalogUnavailableException("down", true, null));

        assertThatThrownBy(() -> syncRange.sync(1, 5, ACTOR))
                .isInstanceOfSatisfying(CatalogUnavailableException.class, e -> assertThat(e.isTimeout()).isTrue());
    }

    @Test
    void reportsConcurrentEditsAsFailuresAndKeepsGoing() {
        when(syncOne.sync("1", ACTOR)).thenThrow(new StaleStoredPokemonException(1));
        when(syncOne.sync("2", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 2));

        SyncReport report = syncRange.sync(1, 2, ACTOR);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.failures()).extracting(SyncReport.Failure::identifier).containsExactly("1");
        assertThat(report.failures().get(0).reason()).isEqualTo("Modified concurrently, retry it");
        assertThat(report.abortedReason()).isNull();
    }

    @Test
    void acceptsTheMaximumRangeSize() {
        when(syncOne.sync(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(ACTOR))).thenReturn(result(SyncOutcome.CREATED, 2));

        assertThat(syncRange.sync(1, SyncPokemonRange.MAX_RANGE, ACTOR).created()).isEqualTo(SyncPokemonRange.MAX_RANGE);
    }

    @Test
    void rejectsInvalidRangesWithoutTouchingUpstream() {
        assertThatThrownBy(() -> syncRange.sync(1, SyncPokemonRange.MAX_RANGE + 1, ACTOR)).isInstanceOf(InvalidSyncRangeException.class);
        assertThatThrownBy(() -> syncRange.sync(5, 4, ACTOR)).isInstanceOf(InvalidSyncRangeException.class);
        assertThatThrownBy(() -> syncRange.sync(0, 3, ACTOR)).isInstanceOf(InvalidSyncRangeException.class);
        assertThatThrownBy(() -> syncRange.sync(-2, 3, ACTOR)).isInstanceOf(InvalidSyncRangeException.class);
        verifyNoInteractions(syncOne);
    }
}
