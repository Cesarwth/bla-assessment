package com.ballastlane.assessment.application.usecase;

import static com.ballastlane.assessment.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.PokeApiGateway;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.SyncOutcome;
import com.ballastlane.assessment.domain.SyncResult;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.support.TestData;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncPokemonTest {

    @Mock PokeApiGateway gateway;
    @Mock PokemonRepository repository;
    SyncPokemon syncPokemon;

    @BeforeEach
    void setUp() {
        syncPokemon = new SyncPokemon(gateway, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsWhenNotStoredYetAndStampsSyncedAtFromTheClock() {
        when(gateway.findDetail("pikachu")).thenReturn(Optional.of(TestData.detail(25, "pikachu")));
        when(repository.findByPokeApiId(25)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = syncPokemon.sync("  Pikachu ");

        assertThat(result.outcome()).isEqualTo(SyncOutcome.CREATED);
        ArgumentCaptor<StoredPokemon> saved = ArgumentCaptor.forClass(StoredPokemon.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().id()).isNull();
        assertThat(saved.getValue().localAttributes()).isEqualTo(LocalAttributes.empty());
        assertThat(saved.getValue().syncedAt()).isEqualTo(NOW);
    }

    @Test
    void refreshesWhenStoredKeepingLocalEdits() {
        LocalAttributes local = new LocalAttributes("Pika", "Kanto", List.of("starter"));
        StoredPokemon existing = TestData.stored(7, 3, 25, "pikachu", local);
        PokemonDetail changed = TestData.detail(25, "pikachu-v2");
        when(gateway.findDetail("25")).thenReturn(Optional.of(changed));
        when(repository.findByPokeApiId(25)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = syncPokemon.sync("25");

        assertThat(result.outcome()).isEqualTo(SyncOutcome.UPDATED);
        assertThat(result.pokemon().id()).isEqualTo(7L);
        assertThat(result.pokemon().version()).isEqualTo(3L);
        assertThat(result.pokemon().name()).isEqualTo("pikachu-v2");
        assertThat(result.pokemon().localAttributes()).isEqualTo(local);
    }

    @Test
    void throwsNotFoundWhenUpstreamHasNothingAndStoresNothing() {
        when(gateway.findDetail("missingno")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> syncPokemon.sync("missingno")).isInstanceOf(PokemonNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidIdentifierBeforeAnyIo() {
        assertThatThrownBy(() -> syncPokemon.sync("../etc")).isInstanceOf(InvalidPokemonIdentifierException.class);
        verifyNoInteractions(gateway, repository);
    }
}
