package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteStoredPokemonTest {

    @Mock PokemonRepository repository;
    DeleteStoredPokemon deleteStoredPokemon;

    @BeforeEach
    void setUp() {
        deleteStoredPokemon = new DeleteStoredPokemon(repository);
    }

    @Test
    void deletesWhenPresent() {
        when(repository.deleteByPokeApiId(25)).thenReturn(true);

        assertThatCode(() -> deleteStoredPokemon.delete(25)).doesNotThrowAnyException();
    }

    @Test
    void throwsNotFoundWhenThereWasNothingToDelete() {
        when(repository.deleteByPokeApiId(25)).thenReturn(false);

        assertThatThrownBy(() -> deleteStoredPokemon.delete(25)).isInstanceOf(StoredPokemonNotFoundException.class);
    }

    @Test
    void rejectsNonPositiveIds() {
        assertThatThrownBy(() -> deleteStoredPokemon.delete(-1)).isInstanceOf(InvalidPokemonIdentifierException.class);
        verifyNoInteractions(repository);
    }
}
