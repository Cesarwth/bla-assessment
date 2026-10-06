package com.ballastlane.assessment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonCommand;
import com.ballastlane.assessment.application.port.out.PokemonRepository;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.exception.InvalidLocalAttributesException;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.InvalidStoredPokemonUpdateException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;
import com.ballastlane.assessment.support.TestData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateStoredPokemonTest {

    private static final LocalAttributes LOCAL = new LocalAttributes("Pika", "Kanto", List.of("starter"));
    private static final long VERSION = 3L;
    private static final Actor OWNER = TestData.user(5);
    private static final StoredPokemon EXISTING = TestData.storedOwnedBy(5L, 7, VERSION, 25, "pikachu", LOCAL);

    @Mock PokemonRepository repository;
    UpdateStoredPokemon updateStoredPokemon;

    @BeforeEach
    void setUp() {
        updateStoredPokemon = new UpdateStoredPokemon(repository);
    }

    private UpdateStoredPokemonCommand command(Long version, Actor actor, String nickname, String region, List<String> tags) {
        return new UpdateStoredPokemonCommand(25, version, nickname, region, tags, actor);
    }

    private StoredPokemon update(String nickname, String region, List<String> tags) {
        when(repository.findByTrainerAndPokeApiId(5L, 25)).thenReturn(Optional.of(EXISTING));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return updateStoredPokemon.update(command(VERSION, OWNER, nickname, region, tags));
    }

    @Test
    void updatesOnlyTheProvidedFields() {
        assertThat(update("Sparky", null, null).localAttributes())
                .isEqualTo(new LocalAttributes("Sparky", "Kanto", List.of("starter")));
    }

    @Test
    void nullFieldsStayUnchanged() {
        StoredPokemon updated = update(null, "Johto", null);

        assertThat(updated.localAttributes().nickname()).isEqualTo("Pika");
        assertThat(updated.localAttributes().region()).isEqualTo("Johto");
        assertThat(updated.localAttributes().tags()).containsExactly("starter");
    }

    @Test
    void emptyStringAndEmptyListClearTheFields() {
        assertThat(update("", "", List.of()).localAttributes()).isEqualTo(LocalAttributes.empty());
    }

    @Test
    void keepsEverythingOwnedByPokeApiUntouchedAndPreservesVersionAndTrainer() {
        update("Sparky", null, null);

        ArgumentCaptor<StoredPokemon> saved = ArgumentCaptor.forClass(StoredPokemon.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue())
                .isEqualTo(EXISTING.withLocalAttributes(new LocalAttributes("Sparky", "Kanto", List.of("starter"))));
        assertThat(saved.getValue().version()).isEqualTo(VERSION);
        assertThat(saved.getValue().ownerId()).isEqualTo(5L);
    }

    @Test
    void anEntryOfAnotherTrainerIsNotFoundAndNothingIsSaved() {
        when(repository.findByTrainerAndPokeApiId(6L, 25)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateStoredPokemon.update(command(VERSION, TestData.user(6), "Mine now", null, null)))
                .isInstanceOf(StoredPokemonNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void aStaleVersionIsRejectedBeforeSaving() {
        when(repository.findByTrainerAndPokeApiId(5L, 25)).thenReturn(Optional.of(EXISTING));

        assertThatThrownBy(() -> updateStoredPokemon.update(command(VERSION - 1, OWNER, "x", null, null)))
                .isInstanceOf(StaleStoredPokemonException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void aMissingVersionIsRejectedWithoutTouchingTheRepository() {
        assertThatThrownBy(() -> updateStoredPokemon.update(command(null, OWNER, "x", null, null)))
                .isInstanceOf(InvalidStoredPokemonUpdateException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAnUpdateWithNothingToChangeBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> updateStoredPokemon.update(command(VERSION, OWNER, null, null, null)))
                .isInstanceOf(InvalidStoredPokemonUpdateException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void throwsNotFoundWhenTheRecordDoesNotExist() {
        when(repository.findByTrainerAndPokeApiId(5L, 25)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateStoredPokemon.update(command(VERSION, OWNER, "x", null, null)))
                .isInstanceOf(StoredPokemonNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidAttributesWithoutSaving() {
        when(repository.findByTrainerAndPokeApiId(5L, 25)).thenReturn(Optional.of(EXISTING));

        assertThatThrownBy(() -> updateStoredPokemon.update(command(VERSION, OWNER, "x".repeat(51), null, null)))
                .isInstanceOf(InvalidLocalAttributesException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsNonPositiveIds() {
        assertThatThrownBy(() -> updateStoredPokemon.update(new UpdateStoredPokemonCommand(0, VERSION, "x", null, null, OWNER)))
                .isInstanceOf(InvalidPokemonIdentifierException.class);
        verifyNoInteractions(repository);
    }
}
