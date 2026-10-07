package com.ballastlane.assessment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.EvolutionNode;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonStat;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.DuplicateStoredPokemonException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import com.ballastlane.assessment.support.TestData;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaPokemonRepositoryAdapter.class, PokemonPersistenceMapper.class, JpaUserRepositoryAdapter.class,
        UserPersistenceMapper.class})
class JpaPokemonRepositoryAdapterTest {

    @Autowired JpaPokemonRepositoryAdapter adapter;
    @Autowired JpaUserRepositoryAdapter users;
    @Autowired EntityManager entityManager;

    private Actor ash;
    private Actor otherTrainer;

    @BeforeEach
    void createTwoTrainers() {
        // user_id is a foreign key to users, so the trainers have to be real rows
        ash = trainer("ash");
        otherTrainer = trainer("other");
    }

    private Actor trainer(String username) {
        User saved = users.save(new User(null, username, username + "@pallet.town", "hash", Role.USER));
        return new Actor(saved.id(), saved.username(), saved.role());
    }

    private StoredPokemon newPokemon(int pokeApiId, String name) {
        return newPokemon(ash, pokeApiId, name);
    }

    private StoredPokemon newPokemon(Actor trainer, int pokeApiId, String name) {
        return StoredPokemon.fromDetail(TestData.detail(pokeApiId, name), TestData.NOW, trainer);
    }

    private Optional<StoredPokemon> find(int pokeApiId) {
        return adapter.findByTrainerAndPokeApiId(ash.userId(), pokeApiId);
    }

    private Object count(String table) {
        return entityManager.createNativeQuery("select count(*) from " + table).getSingleResult();
    }

    /** Forces the next read to hit the database instead of the persistence context. */
    private void reloadFromDatabase() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void savesAndFindsWithEveryCollectionAndLocalField() {
        PokemonDetail detail = TestData.detail(25, "pikachu");
        EvolutionNode chain = new EvolutionNode(172, "pichu", "s/172", List.of(), List.of(
                new EvolutionNode(25, "pikachu", "s/25", List.of("Level up (happiness 220)"), List.of())));
        PokemonDetail withChain = new PokemonDetail(detail.id(), detail.name(), detail.imageUrl(), detail.spriteUrl(),
                List.of("electric", "fairy"), 6.0, 0.4, List.of("static", "lightning-rod"),
                List.of(new PokemonStat("hp", 35), new PokemonStat("speed", 90)), "Mouse Pokémon", "desc", chain);
        StoredPokemon toSave = StoredPokemon.fromDetail(withChain, TestData.NOW, ash)
                .withLocalAttributes(new LocalAttributes("Pika", "Kanto", List.of("starter", "electric")));

        StoredPokemon saved = adapter.save(toSave);
        reloadFromDatabase();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(find(25)).hasValue(saved);
    }

    @Test
    void theTrainerIsStoredAndLoaded() {
        adapter.save(newPokemon(25, "pikachu"));
        reloadFromDatabase();

        assertThat(find(25).orElseThrow().trainerId()).isEqualTo(ash.userId());
    }

    @Test
    void deletingTheUserRemovesTheirEntriesButKeepsTheSharedPokemon() {
        adapter.save(newPokemon(25, "pikachu").withLocalAttributes(new LocalAttributes(null, null, List.of("a"))));
        reloadFromDatabase();

        entityManager.createNativeQuery("delete from users where id = :id").setParameter("id", ash.userId()).executeUpdate();
        entityManager.clear();

        assertThat(count("trainer_pokemon")).isEqualTo(0L);
        assertThat(count("trainer_pokemon_tags")).isEqualTo(0L);
        assertThat(count("pokemon")).isEqualTo(1L);
    }

    @Test
    void twoTrainersKeepTheSamePokemonWithTheirOwnNotesOverOneSharedRow() {
        StoredPokemon mine = adapter.save(newPokemon(ash, 25, "pikachu")
                .withLocalAttributes(new LocalAttributes("Sparky", "Kanto", List.of("mine"))));
        StoredPokemon theirs = adapter.save(newPokemon(otherTrainer, 25, "pikachu")
                .withLocalAttributes(new LocalAttributes("Zap", null, List.of())));
        reloadFromDatabase();

        assertThat(count("pokemon")).isEqualTo(1L);
        assertThat(count("trainer_pokemon")).isEqualTo(2L);
        assertThat(mine.id()).isNotEqualTo(theirs.id());
        assertThat(find(25).orElseThrow().localAttributes().nickname()).isEqualTo("Sparky");
        assertThat(adapter.findByTrainerAndPokeApiId(otherTrainer.userId(), 25).orElseThrow().localAttributes().nickname())
                .isEqualTo("Zap");
    }

    @Test
    void aTrainerCannotHaveTheSamePokemonTwice() {
        adapter.save(newPokemon(25, "pikachu"));

        assertThatThrownBy(() -> adapter.save(newPokemon(25, "pikachu")))
                .isInstanceOf(DuplicateStoredPokemonException.class);
    }

    @Test
    void theNotesOfOneTrainerAreNotVisibleToAnother() {
        adapter.save(newPokemon(25, "pikachu"));
        reloadFromDatabase();

        assertThat(adapter.findByTrainerAndPokeApiId(otherTrainer.userId(), 25)).isEmpty();
        assertThat(adapter.findPage(otherTrainer.userId(), 0, 10).items()).isEmpty();
    }

    @Test
    void refreshingFromAnotherTrainerKeepsTheSharedGenusDescriptionAndChainWhenTheyComeBackEmpty() {
        adapter.save(newPokemon(ash, 25, "pikachu"));
        PokemonDetail partial = new PokemonDetail(25, "pikachu", "img/25", "sprite/25", List.of("electric"), 6.0, 0.4,
                List.of("static"), List.of(new PokemonStat("hp", 35)), null, null, null);

        adapter.save(StoredPokemon.fromDetail(partial, TestData.NOW.plusSeconds(60), otherTrainer));
        reloadFromDatabase();

        StoredPokemon shared = find(25).orElseThrow();
        assertThat(shared.genus()).isEqualTo("Mouse Pokémon");
        assertThat(shared.description()).isEqualTo("description of pikachu");
        assertThat(shared.evolutionChain()).isNotNull();
        assertThat(shared.syncedAt()).isEqualTo(TestData.NOW.plusSeconds(60));
    }

    @Test
    void savingNotesFromAnOlderCopyNeverRollsTheSharedDataBack() {
        StoredPokemon mine = adapter.save(newPokemon(ash, 25, "pikachu"));
        PokemonDetail renamed = TestData.detail(25, "pikachu-v2");
        adapter.save(StoredPokemon.fromDetail(renamed, TestData.NOW.plusSeconds(60), otherTrainer));
        reloadFromDatabase();

        adapter.save(mine.withLocalAttributes(new LocalAttributes("Sparky", null, List.of())));
        reloadFromDatabase();

        StoredPokemon loaded = find(25).orElseThrow();
        assertThat(loaded.name()).isEqualTo("pikachu-v2");
        assertThat(loaded.localAttributes().nickname()).isEqualTo("Sparky");
    }

    @Test
    void anEntryRemovedMeanwhileIsReportedAsStale() {
        StoredPokemon mine = adapter.save(newPokemon(25, "pikachu"));
        adapter.deleteByTrainerAndPokeApiId(ash.userId(), 25);
        reloadFromDatabase();

        assertThatThrownBy(() -> adapter.save(mine.withLocalAttributes(new LocalAttributes("x", null, List.of()))))
                .isInstanceOf(StaleStoredPokemonException.class);
    }

    @Test
    void updatingNotesNeverTouchesTheSharedCatalogRow() {
        StoredPokemon mine = adapter.save(newPokemon(25, "pikachu"));
        reloadFromDatabase();
        Object catalogVersion = entityManager.createNativeQuery("select version from pokemon").getSingleResult();
        PokemonDetail different = new PokemonDetail(25, "renamed", "img", "sprite", List.of("fire", "water"), 1.0, 1.0,
                List.of("other"), List.of(new PokemonStat("hp", 1)), null, null, null);
        // same syncedAt as the stored row: the old save() would rewrite the catalog from this copy
        StoredPokemon edited = mine.refreshedFrom(different, mine.syncedAt())
                .withLocalAttributes(new LocalAttributes("Sparky", "Kanto", List.of("a")));

        StoredPokemon updated = adapter.updateNotes(edited);
        reloadFromDatabase();

        assertThat(entityManager.createNativeQuery("select version from pokemon").getSingleResult()).isEqualTo(catalogVersion);
        StoredPokemon loaded = find(25).orElseThrow();
        assertThat(loaded.name()).isEqualTo("pikachu");
        assertThat(loaded.types()).containsExactly("electric");
        assertThat(loaded.abilities()).containsExactly("static");
        assertThat(loaded.stats()).extracting(PokemonStat::name).containsExactly("hp");
        assertThat(loaded.localAttributes()).isEqualTo(new LocalAttributes("Sparky", "Kanto", List.of("a")));
        assertThat(updated.version()).isEqualTo(mine.version() + 1);
    }

    @Test
    void updatingNotesWithAStaleVersionIsRejected() {
        StoredPokemon initialVersion = adapter.save(newPokemon(25, "pikachu"));
        adapter.updateNotes(initialVersion.withLocalAttributes(new LocalAttributes("first", null, List.of())));

        assertThatThrownBy(() -> adapter.updateNotes(initialVersion.withLocalAttributes(new LocalAttributes("stale", null, List.of()))))
                .isInstanceOf(StaleStoredPokemonException.class);
    }

    @Test
    void updatingNotesOfAnEntryRemovedMeanwhileIsReportedAsStale() {
        StoredPokemon mine = adapter.save(newPokemon(25, "pikachu"));
        adapter.deleteByTrainerAndPokeApiId(ash.userId(), 25);
        reloadFromDatabase();

        assertThatThrownBy(() -> adapter.updateNotes(mine.withLocalAttributes(new LocalAttributes("x", null, List.of()))))
                .isInstanceOf(StaleStoredPokemonException.class);
    }

    @Test
    void updatingNotesOfATransientEntryIsRejected() {
        assertThatThrownBy(() -> adapter.updateNotes(newPokemon(25, "pikachu")))
                .isInstanceOf(StaleStoredPokemonException.class);
    }

    @Test
    void findByPokeApiIdIsEmptyWhenMissing() {
        assertThat(find(999)).isEmpty();
    }

    @Test
    void updateKeepsTheOrderOfTypesAbilitiesAndStats() {
        PokemonDetail base = TestData.detail(1, "bulbasaur");
        PokemonDetail ordered = new PokemonDetail(1, "bulbasaur", base.imageUrl(), base.spriteUrl(),
                List.of("grass", "poison", "ghost"), 6.9, 0.7, List.of("overgrow", "chlorophyll", "x"),
                List.of(new PokemonStat("hp", 45), new PokemonStat("attack", 49), new PokemonStat("defense", 49)),
                "Seed Pokémon", "desc", null);
        StoredPokemon saved = adapter.save(StoredPokemon.fromDetail(ordered, TestData.NOW, ash));
        reloadFromDatabase();

        PokemonDetail reordered = new PokemonDetail(1, "bulbasaur", base.imageUrl(), base.spriteUrl(),
                List.of("ghost", "grass", "poison"), 6.9, 0.7, List.of("x", "overgrow", "chlorophyll"),
                List.of(new PokemonStat("defense", 49), new PokemonStat("hp", 45)), "Seed Pokémon", "desc", null);
        adapter.save(saved.refreshedFrom(reordered, TestData.NOW));
        reloadFromDatabase();

        StoredPokemon loaded = find(1).orElseThrow();
        assertThat(loaded.types()).containsExactly("ghost", "grass", "poison");
        assertThat(loaded.abilities()).containsExactly("x", "overgrow", "chlorophyll");
        assertThat(loaded.stats()).extracting(PokemonStat::name).containsExactly("defense", "hp");
    }

    @Test
    void tagsPersistAndCanBeCleared() {
        StoredPokemon saved = adapter.save(newPokemon(25, "pikachu"));
        StoredPokemon tagged = adapter.save(saved.withLocalAttributes(new LocalAttributes(null, null, List.of("a", "b"))));
        reloadFromDatabase();
        assertThat(find(25).orElseThrow().localAttributes().tags()).containsExactly("a", "b");

        adapter.save(tagged.withLocalAttributes(LocalAttributes.empty()));
        reloadFromDatabase();

        assertThat(find(25).orElseThrow().localAttributes()).isEqualTo(LocalAttributes.empty());
    }

    @Test
    void everyUpdateBumpsTheVersionAndAStaleWriteIsRejected() {
        StoredPokemon initialVersion = adapter.save(newPokemon(25, "pikachu"));
        StoredPokemon versionAfterFirstEdit = adapter.save(initialVersion.withLocalAttributes(new LocalAttributes("first", null, List.of())));

        assertThat(initialVersion.version()).isZero();
        assertThat(versionAfterFirstEdit.version()).isEqualTo(1L);
        assertThatThrownBy(() -> adapter.save(initialVersion.withLocalAttributes(new LocalAttributes("stale", null, List.of()))))
                .isInstanceOf(StaleStoredPokemonException.class);
    }

    @Test
    void findPageOrdersByPokeApiIdAndReportsTheTotalOfTheTrainersOwnEntries() {
        adapter.save(newPokemon(otherTrainer, 4, "charmander"));
        adapter.save(newPokemon(3, "venusaur"));
        adapter.save(newPokemon(1, "bulbasaur"));
        adapter.save(newPokemon(2, "ivysaur"));
        reloadFromDatabase();

        PageResult<StoredPokemon> first = adapter.findPage(ash.userId(), 0, 2);
        PageResult<StoredPokemon> second = adapter.findPage(ash.userId(), 1, 2);

        assertThat(first.items()).extracting(StoredPokemon::pokeApiId).containsExactly(1, 2);
        assertThat(second.items()).extracting(StoredPokemon::pokeApiId).containsExactly(3);
        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
    }

    @Test
    void findPokeApiIdsListsOnlyTheTrainersOwnIdsInAscendingOrder() {
        adapter.save(newPokemon(25, "pikachu"));
        adapter.save(newPokemon(1, "bulbasaur"));
        adapter.save(newPokemon(otherTrainer, 4, "charmander"));
        adapter.save(newPokemon(4, "charmander"));
        reloadFromDatabase();

        assertThat(adapter.findPokeApiIds(ash.userId())).containsExactly(1, 4, 25);
        assertThat(adapter.findPokeApiIds(otherTrainer.userId())).containsExactly(4);
    }

    @Test
    void findPokeApiIdsIsEmptyForATrainerWithoutEntries() {
        assertThat(adapter.findPokeApiIds(otherTrainer.userId())).isEmpty();
    }

    @Test
    void findPageBeyondTheEndIsEmptyEvenForHugePageNumbers() {
        adapter.save(newPokemon(1, "bulbasaur"));

        assertThat(adapter.findPage(ash.userId(), 5, 2).items()).isEmpty();
        PageResult<StoredPokemon> huge = adapter.findPage(ash.userId(), Integer.MAX_VALUE, 50);
        assertThat(huge.items()).isEmpty();
        assertThat(huge.totalItems()).isEqualTo(1);
    }

    @Test
    void deleteRemovesOnlyTheTrainersEntryAndItsTagsAndKeepsTheSharedPokemon() {
        adapter.save(newPokemon(25, "pikachu").withLocalAttributes(new LocalAttributes(null, null, List.of("a"))));
        adapter.save(newPokemon(otherTrainer, 25, "pikachu"));
        reloadFromDatabase();

        assertThat(adapter.deleteByTrainerAndPokeApiId(ash.userId(), 25)).isTrue();
        reloadFromDatabase();

        assertThat(find(25)).isEmpty();
        assertThat(adapter.findByTrainerAndPokeApiId(otherTrainer.userId(), 25)).isPresent();
        assertThat(count("trainer_pokemon")).isEqualTo(1L);
        assertThat(count("trainer_pokemon_tags")).isEqualTo(0L);
        assertThat(count("pokemon")).isEqualTo(1L);
        assertThat(adapter.deleteByTrainerAndPokeApiId(ash.userId(), 25)).isFalse();
    }
}
