package com.ballastlane.assessment.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.support.TestData;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The demo snapshot is data we ship; a typo in it must fail a test, not the first start of the app. */
class DemoDataReaderTest {

    private final List<DemoEntry> entries = new DemoDataReader().read();

    private DemoEntry entry(int pokeApiId) {
        return entries.stream().filter(entry -> entry.detail().id() == pokeApiId).findFirst().orElseThrow();
    }

    @Test
    void readsTheBundledSnapshot() {
        assertThat(entries).hasSize(18);
    }

    @Test
    void noTrainerHasThePokemonTwiceAndEveryEntryBelongsToAKnownDemoAccount() {
        Set<String> perTrainer = entries.stream().map(entry -> entry.trainer() + "/" + entry.detail().id()).collect(Collectors.toSet());

        assertThat(perTrainer).hasSize(entries.size());
        assertThat(entries).extracting(DemoEntry::trainer).containsOnly("demo", "demo-admin");
        assertThat(entries.stream().filter(entry -> entry.trainer().equals("demo"))).hasSizeGreaterThanOrEqualTo(3);
        assertThat(entries.stream().filter(entry -> entry.trainer().equals("demo-admin"))).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void bothTrainersOwnSomeOfTheSamePokemonWithDifferentNotes() {
        List<DemoEntry> bulbasaurs = entries.stream().filter(entry -> entry.detail().id() == 1).toList();
        List<DemoEntry> pikachus = entries.stream().filter(entry -> entry.detail().id() == 25).toList();

        assertThat(bulbasaurs).extracting(DemoEntry::trainer).containsExactlyInAnyOrder("demo", "demo-admin");
        assertThat(pikachus).extracting(DemoEntry::trainer).containsExactlyInAnyOrder("demo", "demo-admin");
        assertThat(bulbasaurs).extracting(DemoEntry::nickname).doesNotHaveDuplicates();
        assertThat(pikachus).extracting(DemoEntry::tags).doesNotHaveDuplicates();
    }

    @Test
    void everyEntryIsAValidAggregateWithValidLocalFields() {
        for (DemoEntry entry : entries) {
            // building them runs the domain invariants (limits on nickname, region, tags)
            LocalAttributes local = new LocalAttributes(entry.nickname(), entry.region(), entry.tags());
            StoredPokemon stored = StoredPokemon.fromDetail(entry.detail(), TestData.NOW, TestData.user(1)).withLocalAttributes(local);

            assertThat(stored.name()).as("name of #%d", entry.detail().id()).isNotBlank();
            assertThat(stored.types()).as("types of %s", stored.name()).isNotEmpty();
            assertThat(stored.stats()).as("stats of %s", stored.name()).hasSize(6);
            assertThat(stored.description()).as("description of %s", stored.name()).isNotBlank();
            assertThat(local.region()).as("region of %s", stored.name()).isNotBlank();
        }
    }

    @Test
    void theRealDataKeepsItsShape() {
        DemoEntry pikachu = entry(25);

        assertThat(pikachu.detail().name()).isEqualTo("pikachu");
        assertThat(pikachu.detail().types()).containsExactly("electric");
        assertThat(pikachu.detail().weightKg()).isEqualTo(6.0);
        assertThat(pikachu.nickname()).isEqualTo("Sparky");
        assertThat(pikachu.trainer()).isEqualTo("demo");
    }

    @Test
    void evolutionLinesAreCarriedAlong() {
        assertThat(entry(25).detail().evolutionChain().name()).isEqualTo("pichu");
        assertThat(entry(133).detail().evolutionChain().evolvesTo()).hasSize(8);
        assertThat(entry(151).detail().evolutionChain().evolvesTo()).isEmpty();
    }

    @Test
    void someEntriesHaveNoNicknameSoTheUiShowsBothKinds() {
        assertThat(entries).anyMatch(entry -> entry.nickname() == null);
        assertThat(entries).anyMatch(entry -> entry.nickname() != null);
    }
}
