package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListStoredPokemonIdsTest {

    private static final Actor ACTOR = TestData.user(5);

    @Mock PokemonRepository repository;

    @Test
    void returnsTheIdsOfTheActorsOwnCollection() {
        when(repository.findPokeApiIds(5L)).thenReturn(List.of(1, 4, 25));

        assertThat(new ListStoredPokemonIds(repository).list(ACTOR)).containsExactly(1, 4, 25);
    }

    @Test
    void anEmptyCollectionGivesAnEmptyList() {
        when(repository.findPokeApiIds(5L)).thenReturn(List.of());

        assertThat(new ListStoredPokemonIds(repository).list(ACTOR)).isEmpty();
    }
}
