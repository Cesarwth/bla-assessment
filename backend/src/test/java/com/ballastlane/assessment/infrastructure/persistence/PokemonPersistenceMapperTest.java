package com.ballastlane.assessment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ballastlane.assessment.domain.EvolutionNode;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;

class PokemonPersistenceMapperTest {

    private final PokemonPersistenceMapper mapper = new PokemonPersistenceMapper();

    private static EvolutionNode node(int id, String name, List<String> requirements, EvolutionNode... next) {
        return new EvolutionNode(id, name, "s/" + id, requirements, List.of(next));
    }

    private TrainerPokemonEntity entryOf(StoredPokemon source, PokemonEntity shared) {
        TrainerPokemonEntity entry = mapper.toNewEntry(source, shared);
        entry.id = source.id();
        entry.version = source.version();
        return entry;
    }

    private StoredPokemon roundTrip(StoredPokemon source) {
        return mapper.toDomain(entryOf(source, mapper.toCatalogEntity(source)));
    }

    @Test
    void roundTripKeepsEveryField() {
        LocalAttributes local = new LocalAttributes("Pika", "Kanto", List.of("starter", "electric"));
        StoredPokemon pokemon = TestData.stored(7, 3, 25, "pikachu", local);

        assertThat(roundTrip(pokemon)).isEqualTo(pokemon);
    }

    @Test
    void roundTripKeepsTheTrainerWhoseCollectionTheEntryIsIn() {
        StoredPokemon entry = TestData.storedOwnedBy(42L, 7, 3, 25, "pikachu", LocalAttributes.empty());

        assertThat(roundTrip(entry).ownerId()).isEqualTo(42L);
    }

    @Test
    void refreshingTheCatalogReplacesCollectionsInsteadOfAppending() {
        StoredPokemon first = TestData.stored(7, 3, 25, "pikachu", LocalAttributes.empty());
        PokemonEntity shared = mapper.toCatalogEntity(first);

        mapper.refreshCatalog(shared, first.refreshedFrom(TestData.detail(25, "pikachu"), first.syncedAt()));

        assertThat(shared.types).containsExactlyElementsOf(first.types());
        assertThat(shared.stats).hasSize(first.stats().size());
    }

    @Test
    void refreshingTheCatalogKeepsGenusDescriptionAndChainWhenTheIncomingOnesAreNull() {
        StoredPokemon complete = TestData.stored(7, 3, 25, "pikachu", LocalAttributes.empty());
        PokemonEntity shared = mapper.toCatalogEntity(complete);
        StoredPokemon partial = new StoredPokemon(7L, 3L, 1L, 25, "pikachu", "s", "i", List.of("electric"), List.of("static"),
                complete.stats(), 6.0, 0.4, null, null, null, LocalAttributes.empty(), complete.syncedAt());

        mapper.refreshCatalog(shared, partial);

        assertThat(shared.genus).isEqualTo(complete.genus());
        assertThat(shared.description).isEqualTo(complete.description());
        assertThat(mapper.toDomain(entryOf(complete, shared)).evolutionChain()).isEqualTo(complete.evolutionChain());
    }

    @Test
    void evolutionChainWithBranchesSurvivesTheJsonColumn() {
        EvolutionNode eevee = node(133, "eevee", List.of(),
                node(134, "vaporeon", List.of("Use water-stone")),
                node(135, "jolteon", List.of("Use thunder-stone"), node(1, "deep", List.of("Level up (level 5)"))));
        StoredPokemon pokemon = TestData.stored(1, 0, 133, "eevee", LocalAttributes.empty());
        StoredPokemon withChain = new StoredPokemon(pokemon.id(), pokemon.version(), pokemon.ownerId(), pokemon.pokeApiId(), pokemon.name(),
                pokemon.spriteUrl(), pokemon.imageUrl(), pokemon.types(), pokemon.abilities(), pokemon.stats(),
                pokemon.weightKg(), pokemon.heightM(), pokemon.genus(), pokemon.description(), eevee,
                pokemon.localAttributes(), pokemon.syncedAt());

        assertThat(roundTrip(withChain).evolutionChain()).isEqualTo(eevee);
    }

    @Test
    void nullEvolutionChainStaysNull() {
        StoredPokemon pokemon = TestData.stored(1, 0, 25, "pikachu", LocalAttributes.empty());
        StoredPokemon noChain = new StoredPokemon(pokemon.id(), pokemon.version(), pokemon.ownerId(), pokemon.pokeApiId(), pokemon.name(),
                pokemon.spriteUrl(), pokemon.imageUrl(), pokemon.types(), pokemon.abilities(), pokemon.stats(),
                pokemon.weightKg(), pokemon.heightM(), pokemon.genus(), pokemon.description(), null,
                pokemon.localAttributes(), pokemon.syncedAt());

        assertThat(roundTrip(noChain).evolutionChain()).isNull();
    }
}
