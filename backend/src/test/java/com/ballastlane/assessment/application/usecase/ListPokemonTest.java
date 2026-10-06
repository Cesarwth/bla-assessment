package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.PokemonSummary;
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

        assertThat(listPokemon.list(null, null)).isSameAs(PAGE);
        verify(gateway).findPage(0, ListPokemon.DEFAULT_SIZE);
    }

    @Test
    void delegatesValidatedValuesToTheGateway() {
        when(gateway.findPage(2, 50)).thenReturn(PAGE);

        assertThat(listPokemon.list(2, 50)).isSameAs(PAGE);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> listPokemon.list(-1, 10)).isInstanceOf(InvalidPaginationException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsSizeBelowOne() {
        assertThatThrownBy(() -> listPokemon.list(0, 0)).isInstanceOf(InvalidPaginationException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsSizeAboveTheMaximum() {
        assertThatThrownBy(() -> listPokemon.list(0, ListPokemon.MAX_SIZE + 1))
                .isInstanceOf(InvalidPaginationException.class);
        verifyNoInteractions(gateway);
    }
}
