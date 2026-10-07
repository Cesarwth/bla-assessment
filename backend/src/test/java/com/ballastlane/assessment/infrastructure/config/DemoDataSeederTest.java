package com.ballastlane.assessment.infrastructure.config;

import static com.ballastlane.assessment.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.application.port.out.UserRepository;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.User;
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
class DemoDataSeederTest {

    private static final User DEMO = new User(1L, "demo", "demo@pokemon.dev", "hash", Role.USER);
    private static final User ADMIN = new User(2L, "demo-admin", "admin@pokemon.dev", "hash", Role.ADMIN);

    @Mock PokemonRepository repository;
    @Mock UserRepository users;
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private static DemoEntry entry(String trainer, int id, String name, String nickname, String region, List<String> tags) {
        return new DemoEntry(trainer, nickname, region, tags, TestData.detail(id, name));
    }

    private DemoDataSeeder seeder(DemoEntry... entries) {
        return new DemoDataSeeder(List.of(entries), repository, users, clock);
    }

    @BeforeEach
    void noStoredPokemonYet() {
        org.mockito.Mockito.lenient().when(repository.findByTrainerAndPokeApiId(anyLong(), anyInt())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void storesEachEntryOwnedByItsUser() {
        when(users.findByUsername("demo")).thenReturn(Optional.of(DEMO));
        when(users.findByUsername("demo-admin")).thenReturn(Optional.of(ADMIN));

        seeder(entry("demo", 25, "pikachu", null, "Kanto", List.of()), entry("demo-admin", 26, "raichu", null, "Kanto", List.of()))
                .run(null);

        ArgumentCaptor<StoredPokemon> saved = ArgumentCaptor.forClass(StoredPokemon.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(StoredPokemon::pokeApiId, StoredPokemon::trainerId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(25, 1L), org.assertj.core.groups.Tuple.tuple(26, 2L));
    }

    @Test
    void appliesNicknameRegionAndTagsAndStampsTheTimeFromTheClock() {
        when(users.findByUsername("demo")).thenReturn(Optional.of(DEMO));

        seeder(entry("demo", 25, "pikachu", "Sparky", "Viridian Forest", List.of("favorite", "electric"))).run(null);

        ArgumentCaptor<StoredPokemon> saved = ArgumentCaptor.forClass(StoredPokemon.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().localAttributes())
                .isEqualTo(new LocalAttributes("Sparky", "Viridian Forest", List.of("favorite", "electric")));
        assertThat(saved.getValue().syncedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().id()).isNull(); // a new row, never an overwrite
    }

    @Test
    void skipsWhatTheTrainerAlreadyHasSoRestartsNeverUndoUsersEdits() {
        when(users.findByUsername("demo")).thenReturn(Optional.of(DEMO));
        when(repository.findByTrainerAndPokeApiId(1L, 25))
                .thenReturn(Optional.of(TestData.stored(7, 3, 25, "pikachu", new LocalAttributes("Edited", null, List.of()))));

        seeder(entry("demo", 25, "pikachu", "Sparky", "Kanto", List.of())).run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void twoTrainersCanBothHaveTheSamePokemonWithDifferentNotes() {
        when(users.findByUsername("demo")).thenReturn(Optional.of(DEMO));
        when(users.findByUsername("demo-admin")).thenReturn(Optional.of(ADMIN));
        when(repository.findByTrainerAndPokeApiId(2L, 25)).thenReturn(Optional.empty());

        seeder(entry("demo", 25, "pikachu", "Sparky", "Kanto", List.of()), entry("demo-admin", 25, "pikachu", null, "Johto", List.of("mascot")))
                .run(null);

        ArgumentCaptor<StoredPokemon> saved = ArgumentCaptor.forClass(StoredPokemon.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(StoredPokemon::trainerId).containsExactly(1L, 2L);
        assertThat(saved.getAllValues()).extracting(stored -> stored.localAttributes().nickname()).containsExactly("Sparky", null);
    }

    @Test
    void skipsEntriesWhoseOwnerDoesNotExistAndKeepsTheRest() {
        when(users.findByUsername("demo")).thenReturn(Optional.of(DEMO));
        when(users.findByUsername("demo-admin")).thenReturn(Optional.empty());

        seeder(entry("demo", 25, "pikachu", null, "Kanto", List.of()), entry("demo-admin", 26, "raichu", null, "Kanto", List.of()))
                .run(null);

        ArgumentCaptor<StoredPokemon> saved = ArgumentCaptor.forClass(StoredPokemon.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().pokeApiId()).isEqualTo(25);
    }

    @Test
    void doesNothingWithoutEntries() {
        new DemoDataSeeder(List.of(), repository, users, clock).run(null);

        verifyNoInteractions(repository, users);
    }
}
