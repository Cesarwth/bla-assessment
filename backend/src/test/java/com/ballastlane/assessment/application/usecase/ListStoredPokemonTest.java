package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.exception.InvalidPaginationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListStoredPokemonTest {

    private static final PageResult<StoredPokemon> PAGE = new PageResult<>(List.of(), 0, 12, 0);

    @Mock PokemonRepository repository;
    ListStoredPokemon listStoredPokemon;

    @BeforeEach
    void setUp() {
        listStoredPokemon = new ListStoredPokemon(repository);
    }

    @Test
    void usesTheSameDefaultsAsTheCatalog() {
        when(repository.findPage(0, ListPokemon.DEFAULT_SIZE)).thenReturn(PAGE);

        assertThat(listStoredPokemon.list(null, null)).isSameAs(PAGE);
    }

    @Test
    void returnsTheRepositoryPageForValidInput() {
        when(repository.findPage(3, 50)).thenReturn(PAGE);

        assertThat(listStoredPokemon.list(3, 50)).isSameAs(PAGE);
    }

    @Test
    void rejectsInvalidPaginationWithoutTouchingTheRepository() {
        assertThatThrownBy(() -> listStoredPokemon.list(-1, 10)).isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> listStoredPokemon.list(0, 0)).isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> listStoredPokemon.list(0, ListPokemon.MAX_SIZE + 1))
                .isInstanceOf(InvalidPaginationException.class);
        verifyNoInteractions(repository);
    }
}
