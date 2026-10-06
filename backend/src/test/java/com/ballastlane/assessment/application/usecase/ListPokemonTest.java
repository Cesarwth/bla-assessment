package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.PokemonSearchQuery;
import com.ballastlane.assessment.domain.PokemonSummary;
import com.ballastlane.assessment.domain.exception.InvalidSearchQueryException;
import com.ballastlane.assessment.domain.exception.InvalidPaginationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListPokemonTest {

    private static final PageResult<PokemonSummary> PAGE = new PageResult<>(List.of(), 0, 12, 0);

    @Mock PokeApiGateway gateway;
    ListPokemon listPokemon;

    @BeforeEach
    void setUp() {
        listPokemon = new ListPokemon(gateway);
    }

    @Test
    void usesDefaultsWhenParametersAreMissing() {
        when(gateway.findPage(0, 12)).thenReturn(PAGE);

        assertThat(listPokemon.list(null, null, null)).isSameAs(PAGE);
        verify(gateway).findPage(0, ListPokemon.DEFAULT_SIZE);
    }

    @Test
    void delegatesValidatedValuesToTheGateway() {
        when(gateway.findPage(2, 50)).thenReturn(PAGE);

        assertThat(listPokemon.list(2, 50, null)).isSameAs(PAGE);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> listPokemon.list(-1, 10, null)).isInstanceOf(InvalidPaginationException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsSizeBelowOne() {
        assertThatThrownBy(() -> listPokemon.list(0, 0, null)).isInstanceOf(InvalidPaginationException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsSizeAboveTheMaximum() {
        assertThatThrownBy(() -> listPokemon.list(0, ListPokemon.MAX_SIZE + 1, null))
                .isInstanceOf(InvalidPaginationException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void blankQueryBehavesLikeNoQuery() {
        when(gateway.findPage(0, 12)).thenReturn(PAGE);

        assertThat(listPokemon.list(null, null, "   ")).isSameAs(PAGE);
        verify(gateway, never()).search(any(), anyInt(), anyInt());
    }

    @Test
    void searchesWithTheNormalizedQuery() {
        when(gateway.search(PokemonSearchQuery.of("mr-mime").orElseThrow(), 1, 20)).thenReturn(PAGE);

        assertThat(listPokemon.list(1, 20, "  Mr Mime ")).isSameAs(PAGE);
        verify(gateway, never()).findPage(anyInt(), anyInt());
    }

    @Test
    void rejectsTooLongQueryBeforeTouchingTheGateway() {
        assertThatThrownBy(() -> listPokemon.list(0, 10, "a".repeat(51))).isInstanceOf(InvalidSearchQueryException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void paginationIsStillValidatedWhenSearching() {
        assertThatThrownBy(() -> listPokemon.list(0, 51, "saur")).isInstanceOf(InvalidPaginationException.class);
        verifyNoInteractions(gateway);
    }
}
