package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.ballastlane.assessment.domain.exception.DuplicateStoredPokemonException;
import com.ballastlane.assessment.domain.exception.InvalidSyncSelectionException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import com.ballastlane.assessment.support.TestData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncSelectedPokemonTest {

    private static final Actor ACTOR = TestData.user(5);

    private static final Duration BUDGET = Duration.ofSeconds(90);

    @Mock SyncPokemonUseCase syncOne;
    MutableClock clock = new MutableClock();
    SyncSelectedPokemon syncSelected;

    @BeforeEach
    void setUp() {
        syncSelected = new SyncSelectedPokemon(syncOne, clock, BUDGET);
    }

    /** Time only moves when a test says so, so the budget is exercised without sleeping. */
    static final class MutableClock extends Clock {
        private Instant now = TestData.NOW;

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static SyncResult result(SyncOutcome outcome, int id) {
        return new SyncResult(outcome, TestData.stored(id, 0, id, "p" + id, LocalAttributes.empty()));
    }

    @Test
    void syncsEveryIdInTheSelectionAndCountsOutcomes() {
        when(syncOne.sync("1", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 1));
        when(syncOne.sync("2", ACTOR)).thenReturn(result(SyncOutcome.UPDATED, 2));
        when(syncOne.sync("3", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 3));

        SyncReport report = syncSelected.sync(List.of(1, 2, 3), ACTOR);

        assertThat(report.created()).isEqualTo(2);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void everyIdIsSyncedOnBehalfOfTheSameActor() {
        Actor other = TestData.user(9);
        when(syncOne.sync("1", other)).thenReturn(result(SyncOutcome.CREATED, 1));
        when(syncOne.sync("2", other)).thenReturn(result(SyncOutcome.CREATED, 2));

        assertThat(syncSelected.sync(List.of(1, 2), other).created()).isEqualTo(2);
    }

    @Test
    void reportsIdsMissingUpstreamAsFailuresAndKeepsGoing() {
        when(syncOne.sync("1025", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 1025));
        when(syncOne.sync("1026", ACTOR)).thenThrow(new PokemonNotFoundException("1026"));
        when(syncOne.sync("1027", ACTOR)).thenThrow(new PokemonNotFoundException("1027"));

        SyncReport report = syncSelected.sync(List.of(1025, 1026, 1027), ACTOR);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.failures()).extracting(SyncReport.Failure::identifier).containsExactly("1026", "1027");
        assertThat(report.failures().get(0).reason()).isEqualTo("Not found upstream");
    }

    @Test
    void returnsAPartialReportWhenTheCatalogGoesDownMidway() {
        when(syncOne.sync("1", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 1));
        when(syncOne.sync("2", ACTOR)).thenThrow(new CatalogUnavailableException("down", false, null));

        SyncReport report = syncSelected.sync(List.of(1, 2, 3, 4, 5), ACTOR);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.abortedReason()).contains("2");
        verify(syncOne, never()).sync("3", ACTOR);
    }

    @Test
    void rethrowsWhenTheCatalogIsDownBeforeAnythingWasProcessed() {
        when(syncOne.sync("1", ACTOR)).thenThrow(new CatalogUnavailableException("down", true, null));

        assertThatThrownBy(() -> syncSelected.sync(List.of(1, 2, 3, 4, 5), ACTOR))
                .isInstanceOfSatisfying(CatalogUnavailableException.class, exception -> assertThat(exception.isTimeout()).isTrue());
    }

    @Test
    void reportsConcurrentEditsAsFailuresAndKeepsGoing() {
        when(syncOne.sync("1", ACTOR)).thenThrow(new StaleStoredPokemonException(1));
        when(syncOne.sync("2", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 2));

        SyncReport report = syncSelected.sync(List.of(1, 2), ACTOR);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.failures()).extracting(SyncReport.Failure::identifier).containsExactly("1");
        assertThat(report.failures().get(0).reason()).isEqualTo("Modified concurrently, retry it");
        assertThat(report.abortedReason()).isNull();
    }

    @Test
    void stopsWithAPartialReportWhenTheTimeBudgetIsExceeded() {
        when(syncOne.sync("1", ACTOR)).thenAnswer(inv -> {
            clock.advance(Duration.ofSeconds(60));
            return result(SyncOutcome.CREATED, 1);
        });
        when(syncOne.sync("2", ACTOR)).thenAnswer(inv -> {
            clock.advance(Duration.ofSeconds(30));
            return result(SyncOutcome.UPDATED, 2);
        });

        SyncReport report = syncSelected.sync(List.of(1, 2, 3, 4), ACTOR);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.abortedReason()).isEqualTo("Time budget exceeded before id 3");
        verify(syncOne, never()).sync("3", ACTOR);
        verify(syncOne, never()).sync("4", ACTOR);
    }

    @Test
    void finishingExactlyInsideTheBudgetAbortsNothing() {
        when(syncOne.sync("1", ACTOR)).thenAnswer(inv -> {
            clock.advance(BUDGET.minusMillis(1));
            return result(SyncOutcome.CREATED, 1);
        });
        when(syncOne.sync("2", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 2));

        SyncReport report = syncSelected.sync(List.of(1, 2), ACTOR);

        assertThat(report.created()).isEqualTo(2);
        assertThat(report.abortedReason()).isNull();
    }

    @Test
    void theFirstIdIsAlwaysAttemptedEvenWithATinyBudget() {
        when(syncOne.sync("1", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 1));

        SyncReport report = new SyncSelectedPokemon(syncOne, clock, Duration.ofMillis(1)).sync(List.of(1), ACTOR);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.abortedReason()).isNull();
    }

    @Test
    void reportsAnEntryBeingAddedConcurrentlyAsAFailureAndKeepsGoing() {
        when(syncOne.sync("1", ACTOR)).thenThrow(new DuplicateStoredPokemonException(1));
        when(syncOne.sync("2", ACTOR)).thenReturn(result(SyncOutcome.CREATED, 2));

        SyncReport report = syncSelected.sync(List.of(1, 2), ACTOR);

        assertThat(report.created()).isEqualTo(1);
        assertThat(report.failures()).containsExactly(new SyncReport.Failure("1", "Already being added, retry it"));
        assertThat(report.abortedReason()).isNull();
    }

    @Test
    void keepsTheOrderGivenAndSyncsEachDistinctIdOnce() {
        when(syncOne.sync(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(ACTOR)))
                .thenReturn(result(SyncOutcome.CREATED, 1));

        SyncReport report = syncSelected.sync(List.of(7, 1, 7, 4, 1), ACTOR);

        assertThat(report.created()).isEqualTo(3);
        var order = org.mockito.Mockito.inOrder(syncOne);
        order.verify(syncOne).sync("7", ACTOR);
        order.verify(syncOne).sync("1", ACTOR);
        order.verify(syncOne).sync("4", ACTOR);
        verify(syncOne, times(1)).sync("7", ACTOR);
        verify(syncOne, times(1)).sync("1", ACTOR);
    }

    @Test
    void acceptsTheMaximumSelectionSize() {
        when(syncOne.sync(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(ACTOR))).thenReturn(result(SyncOutcome.CREATED, 2));
        List<Integer> ids = IntStream.rangeClosed(1, SyncSelectedPokemon.MAX_SELECTION).boxed().toList();

        assertThat(syncSelected.sync(ids, ACTOR).created()).isEqualTo(SyncSelectedPokemon.MAX_SELECTION);
    }

    @Test
    void rejectsInvalidSelectionsWithoutTouchingUpstream() {
        List<Integer> tooMany = new ArrayList<>(IntStream.rangeClosed(1, SyncSelectedPokemon.MAX_SELECTION + 1).boxed().toList());

        assertThatThrownBy(() -> syncSelected.sync(null, ACTOR)).isInstanceOf(InvalidSyncSelectionException.class);
        assertThatThrownBy(() -> syncSelected.sync(List.of(), ACTOR)).isInstanceOf(InvalidSyncSelectionException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> syncSelected.sync(tooMany, ACTOR)).isInstanceOf(InvalidSyncSelectionException.class)
                .hasMessageContaining("at most " + SyncSelectedPokemon.MAX_SELECTION);
        assertThatThrownBy(() -> syncSelected.sync(List.of(1, 0), ACTOR)).isInstanceOf(InvalidSyncSelectionException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> syncSelected.sync(List.of(-2), ACTOR)).isInstanceOf(InvalidSyncSelectionException.class);
        assertThatThrownBy(() -> syncSelected.sync(Arrays.asList(1, null), ACTOR)).isInstanceOf(InvalidSyncSelectionException.class);
        verifyNoInteractions(syncOne);
    }
}
