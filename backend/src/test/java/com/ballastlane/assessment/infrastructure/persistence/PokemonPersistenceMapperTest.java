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

    @Test
    void roundTripKeepsEveryField() {
        LocalAttributes local = new LocalAttributes("Pika", "Kanto", List.of("starter", "electric"));
        StoredPokemon pokemon = TestData.stored(7, 3, 25, "pikachu", local);

        assertThat(mapper.toDomain(mapper.toEntity(pokemon))).isEqualTo(pokemon);
    }

    @Test
    void roundTripKeepsTheOwnerAndAnOwnerlessRecordStaysOwnerless() {
        StoredPokemon owned = TestData.storedOwnedBy(42L, 7, 3, 25, "pikachu", LocalAttributes.empty());
        StoredPokemon ownerless = TestData.storedOwnedBy(null, 8, 0, 26, "raichu", LocalAttributes.empty());

        assertThat(mapper.toDomain(mapper.toEntity(owned)).ownerId()).isEqualTo(42L);
        assertThat(mapper.toDomain(mapper.toEntity(ownerless)).ownerId()).isNull();
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

        assertThat(mapper.toDomain(mapper.toEntity(withChain)).evolutionChain()).isEqualTo(eevee);
    }

    @Test
    void nullEvolutionChainStaysNull() {
        StoredPokemon pokemon = TestData.stored(1, 0, 25, "pikachu", LocalAttributes.empty());
        StoredPokemon noChain = new StoredPokemon(pokemon.id(), pokemon.version(), pokemon.ownerId(), pokemon.pokeApiId(), pokemon.name(),
                pokemon.spriteUrl(), pokemon.imageUrl(), pokemon.types(), pokemon.abilities(), pokemon.stats(),
                pokemon.weightKg(), pokemon.heightM(), pokemon.genus(), pokemon.description(), null,
                pokemon.localAttributes(), pokemon.syncedAt());

        assertThat(mapper.toDomain(mapper.toEntity(noChain)).evolutionChain()).isNull();
    }
}
