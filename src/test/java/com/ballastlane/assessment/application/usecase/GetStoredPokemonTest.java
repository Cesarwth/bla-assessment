package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;
import com.ballastlane.assessment.support.TestData;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetStoredPokemonTest {

    @Mock PokemonRepository repository;
    GetStoredPokemon getStoredPokemon;

    @BeforeEach
    void setUp() {
        getStoredPokemon = new GetStoredPokemon(repository);
    }

    @Test
    void returnsTheStoredPokemon() {
        StoredPokemon stored = TestData.stored(7, 0, 25, "pikachu", LocalAttributes.empty());
        when(repository.findByPokeApiId(25)).thenReturn(Optional.of(stored));

        assertThat(getStoredPokemon.get(25)).isSameAs(stored);
    }

    @Test
    void throwsNotFoundWhenMissing() {
        when(repository.findByPokeApiId(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getStoredPokemon.get(99)).isInstanceOf(StoredPokemonNotFoundException.class);
    }

    @Test
    void rejectsNonPositiveIds() {
        assertThatThrownBy(() -> getStoredPokemon.get(0)).isInstanceOf(InvalidPokemonIdentifierException.class);
        verifyNoInteractions(repository);
    }
}
