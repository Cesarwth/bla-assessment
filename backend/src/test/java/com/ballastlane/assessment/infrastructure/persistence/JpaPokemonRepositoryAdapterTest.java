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
    @Autowired EntityManager em;

    private Actor owner;

    @BeforeEach
    void createAnOwnerUser() {
        // owner_id is a foreign key to users, so the owner has to be a real row
        User saved = users.save(new User(null, "owner", "owner@pallet.town", "hash", Role.USER));
        owner = new Actor(saved.id(), saved.username(), saved.role());
    }

    private StoredPokemon newPokemon(int pokeApiId, String name) {
        return StoredPokemon.fromDetail(TestData.detail(pokeApiId, name), TestData.NOW, owner);
    }

    /** Forces the next read to hit the database instead of the persistence context. */
    private void reloadFromDatabase() {
        em.flush();
        em.clear();
    }

    @Test
    void savesAndFindsWithEveryCollectionAndLocalField() {
        PokemonDetail detail = TestData.detail(25, "pikachu");
        EvolutionNode chain = new EvolutionNode(172, "pichu", "s/172", List.of(), List.of(
                new EvolutionNode(25, "pikachu", "s/25", List.of("Level up (happiness 220)"), List.of())));
        PokemonDetail withChain = new PokemonDetail(detail.id(), detail.name(), detail.imageUrl(), detail.spriteUrl(),
                List.of("electric", "fairy"), 6.0, 0.4, List.of("static", "lightning-rod"),
                List.of(new PokemonStat("hp", 35), new PokemonStat("speed", 90)), "Mouse Pokémon", "desc", chain);
        StoredPokemon toSave = StoredPokemon.fromDetail(withChain, TestData.NOW, owner)
                .withLocalAttributes(new LocalAttributes("Pika", "Kanto", List.of("starter", "electric")));

        StoredPokemon saved = adapter.save(toSave);
        reloadFromDatabase();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(adapter.findByPokeApiId(25)).hasValue(saved);
    }

    @Test
    void theOwnerIsStoredAndLoaded() {
        adapter.save(newPokemon(25, "pikachu"));
        reloadFromDatabase();

        assertThat(adapter.findByPokeApiId(25).orElseThrow().ownerId()).isEqualTo(owner.userId());
    }

    @Test
    void deletingTheOwnerUserKeepsTheRecordButWithoutOwner() {
        adapter.save(newPokemon(25, "pikachu"));
        reloadFromDatabase();

        em.createNativeQuery("delete from users where id = :id").setParameter("id", owner.userId()).executeUpdate();
        em.clear();

        assertThat(adapter.findByPokeApiId(25)).hasValueSatisfying(found -> assertThat(found.ownerId()).isNull());
    }

    @Test
    void findByPokeApiIdIsEmptyWhenMissing() {
        assertThat(adapter.findByPokeApiId(999)).isEmpty();
    }

    @Test
    void pokeApiIdIsUnique() {
        adapter.save(newPokemon(25, "pikachu"));

        assertThatThrownBy(() -> adapter.save(newPokemon(25, "pikachu-clone")))
                .isInstanceOf(DuplicateStoredPokemonException.class);
    }

    @Test
    void updateKeepsTheOrderOfTypesAbilitiesAndStats() {
        PokemonDetail base = TestData.detail(1, "bulbasaur");
        PokemonDetail ordered = new PokemonDetail(1, "bulbasaur", base.imageUrl(), base.spriteUrl(),
                List.of("grass", "poison", "ghost"), 6.9, 0.7, List.of("overgrow", "chlorophyll", "x"),
                List.of(new PokemonStat("hp", 45), new PokemonStat("attack", 49), new PokemonStat("defense", 49)),
                "Seed Pokémon", "desc", null);
        StoredPokemon saved = adapter.save(StoredPokemon.fromDetail(ordered, TestData.NOW, owner));
        reloadFromDatabase();

        PokemonDetail reordered = new PokemonDetail(1, "bulbasaur", base.imageUrl(), base.spriteUrl(),
                List.of("ghost", "grass", "poison"), 6.9, 0.7, List.of("x", "overgrow", "chlorophyll"),
                List.of(new PokemonStat("defense", 49), new PokemonStat("hp", 45)), "Seed Pokémon", "desc", null);
        adapter.save(saved.refreshedFrom(reordered, TestData.NOW));
        reloadFromDatabase();

        StoredPokemon loaded = adapter.findByPokeApiId(1).orElseThrow();
        assertThat(loaded.types()).containsExactly("ghost", "grass", "poison");
        assertThat(loaded.abilities()).containsExactly("x", "overgrow", "chlorophyll");
        assertThat(loaded.stats()).extracting(PokemonStat::name).containsExactly("defense", "hp");
    }

    @Test
    void tagsPersistAndCanBeCleared() {
        StoredPokemon saved = adapter.save(newPokemon(25, "pikachu"));
        StoredPokemon tagged = adapter.save(saved.withLocalAttributes(new LocalAttributes(null, null, List.of("a", "b"))));
        reloadFromDatabase();
        assertThat(adapter.findByPokeApiId(25).orElseThrow().localAttributes().tags()).containsExactly("a", "b");

        adapter.save(tagged.withLocalAttributes(LocalAttributes.empty()));
        reloadFromDatabase();

        assertThat(adapter.findByPokeApiId(25).orElseThrow().localAttributes()).isEqualTo(LocalAttributes.empty());
    }

    @Test
    void everyUpdateBumpsTheVersionAndAStaleWriteIsRejected() {
        StoredPokemon v0 = adapter.save(newPokemon(25, "pikachu"));
        StoredPokemon v1 = adapter.save(v0.withLocalAttributes(new LocalAttributes("first", null, List.of())));

        assertThat(v0.version()).isZero();
        assertThat(v1.version()).isEqualTo(1L);
        assertThatThrownBy(() -> adapter.save(v0.withLocalAttributes(new LocalAttributes("stale", null, List.of()))))
                .isInstanceOf(StaleStoredPokemonException.class);
    }

    @Test
    void findPageOrdersByPokeApiIdAndReportsTheTotal() {
        adapter.save(newPokemon(3, "venusaur"));
        adapter.save(newPokemon(1, "bulbasaur"));
        adapter.save(newPokemon(2, "ivysaur"));
        reloadFromDatabase();

        PageResult<StoredPokemon> first = adapter.findPage(0, 2);
        PageResult<StoredPokemon> second = adapter.findPage(1, 2);

        assertThat(first.items()).extracting(StoredPokemon::pokeApiId).containsExactly(1, 2);
        assertThat(second.items()).extracting(StoredPokemon::pokeApiId).containsExactly(3);
        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
    }

    @Test
    void findPageBeyondTheEndIsEmptyEvenForHugePageNumbers() {
        adapter.save(newPokemon(1, "bulbasaur"));

        assertThat(adapter.findPage(5, 2).items()).isEmpty();
        PageResult<StoredPokemon> huge = adapter.findPage(Integer.MAX_VALUE, 50);
        assertThat(huge.items()).isEmpty();
        assertThat(huge.totalItems()).isEqualTo(1);
    }

    @Test
    void deleteRemovesTheRowAndItsChildRows() {
        adapter.save(newPokemon(25, "pikachu").withLocalAttributes(new LocalAttributes(null, null, List.of("a"))));
        reloadFromDatabase();

        assertThat(adapter.deleteByPokeApiId(25)).isTrue();
        reloadFromDatabase();

        assertThat(adapter.findByPokeApiId(25)).isEmpty();
        for (String table : List.of("pokemon_types", "pokemon_abilities", "pokemon_stats", "pokemon_tags")) {
            assertThat(em.createNativeQuery("select count(*) from " + table).getSingleResult())
                    .as(table).isEqualTo(0L);
        }
        assertThat(adapter.deleteByPokeApiId(25)).isFalse();
    }
}
