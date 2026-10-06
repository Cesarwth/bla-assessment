package com.ballastlane.assessment.domain;

import static com.ballastlane.assessment.support.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.ballastlane.assessment.support.TestData;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoredPokemonTest {

    private static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    void createdFromDetailHasNoIdentityAndEmptyLocalAttributes() {
        StoredPokemon created = StoredPokemon.fromDetail(TestData.detail(25, "pikachu"), NOW);

        assertThat(created.id()).isNull();
        assertThat(created.version()).isNull();
        assertThat(created.pokeApiId()).isEqualTo(25);
        assertThat(created.name()).isEqualTo("pikachu");
        assertThat(created.localAttributes()).isEqualTo(LocalAttributes.empty());
        assertThat(created.syncedAt()).isEqualTo(NOW);
    }

    @Test
    void refreshKeepsIdentityVersionAndLocalAttributes() {
        LocalAttributes local = new LocalAttributes("Pika", "Kanto", List.of("starter"));
        StoredPokemon existing = TestData.stored(7, 3, 25, "pikachu", local);

        StoredPokemon refreshed = existing.refreshedFrom(TestData.detail(25, "pikachu"), LATER);

        assertThat(refreshed.id()).isEqualTo(7L);
        assertThat(refreshed.version()).isEqualTo(3L);
        assertThat(refreshed.localAttributes()).isEqualTo(local);
        assertThat(refreshed.syncedAt()).isEqualTo(LATER);
    }

    @Test
    void refreshReplacesEveryPokeApiOwnedField() {
        StoredPokemon existing = TestData.stored(7, 3, 25, "pikachu", LocalAttributes.empty());
        PokemonDetail changed = new PokemonDetail(25, "pikachu-renamed", "new-img", "new-sprite", List.of("electric", "fairy"),
                7.5, 0.5, List.of("static", "lightning-rod"), List.of(new PokemonStat("hp", 99)), "New genus",
                "New description", null);

        StoredPokemon refreshed = existing.refreshedFrom(changed, LATER);

        assertThat(refreshed.name()).isEqualTo("pikachu-renamed");
        assertThat(refreshed.imageUrl()).isEqualTo("new-img");
        assertThat(refreshed.types()).containsExactly("electric", "fairy");
        assertThat(refreshed.abilities()).containsExactly("static", "lightning-rod");
        assertThat(refreshed.stats()).containsExactly(new PokemonStat("hp", 99));
        assertThat(refreshed.weightKg()).isEqualTo(7.5);
        assertThat(refreshed.heightM()).isEqualTo(0.5);
        assertThat(refreshed.genus()).isEqualTo("New genus");
        assertThat(refreshed.description()).isEqualTo("New description");
        assertThat(refreshed.evolutionChain()).isNull();
    }

    @Test
    void withLocalAttributesChangesOnlyThem() {
        StoredPokemon existing = TestData.stored(7, 3, 25, "pikachu", LocalAttributes.empty());
        LocalAttributes local = new LocalAttributes("Pika", null, List.of());

        StoredPokemon updated = existing.withLocalAttributes(local);

        assertThat(updated.localAttributes()).isEqualTo(local);
        assertThat(updated.withLocalAttributes(LocalAttributes.empty())).isEqualTo(existing);
    }
}
