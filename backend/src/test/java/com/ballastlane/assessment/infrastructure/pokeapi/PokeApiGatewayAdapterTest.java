package com.ballastlane.assessment.infrastructure.pokeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonSearchQuery;
import com.ballastlane.assessment.domain.PokemonSummary;
import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPage;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PokeApiGatewayAdapterTest {

    @Mock PokeApiClient client;
    ExecutorService executor;
    PokeApiGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        adapter = new PokeApiGatewayAdapter(client, new PokeApiMapper(), executor);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    private static PokeApiPokemon named(int id, String name) {
        PokeApiPokemon base = Fixtures.pokemon("pokemon-25.json");
        return new PokeApiPokemon(id, name, base.height(), base.weight(), base.types(), base.abilities(), base.stats(),
                base.sprites(), base.species());
    }

    @Test
    void listKeepsPageOrderEvenWhenEarlierCallsFinishLast() {
        when(client.getPage(3, 0)).thenReturn(Fixtures.page("list.json"));
        // Id 1 only finishes after 2 and 3 did, so completion order is the reverse of page order.
        CountDownLatch othersFinished = new CountDownLatch(2);
        when(client.getPokemon("1")).thenAnswer(inv -> {
            othersFinished.await(5, TimeUnit.SECONDS);
            return Optional.of(named(1, "bulbasaur"));
        });
        when(client.getPokemon("2")).thenAnswer(inv -> {
            othersFinished.countDown();
            return Optional.of(named(2, "ivysaur"));
        });
        when(client.getPokemon("3")).thenAnswer(inv -> {
            othersFinished.countDown();
            return Optional.of(named(3, "venusaur"));
        });

        PageResult<PokemonSummary> page = adapter.findPage(0, 3);

        assertThat(page.items()).extracting(PokemonSummary::name).containsExactly("bulbasaur", "ivysaur", "venusaur");
        assertThat(page.totalItems()).isEqualTo(1351);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(3);
    }

    @Test
    void listTranslatesPageAndSizeIntoLimitAndOffset() {
        when(client.getPage(12, 24)).thenReturn(new PokeApiPage(1351, List.of()));

        adapter.findPage(2, 12);

        verify(client).getPage(12, 24);
    }

    @Test
    void listClampsAnOffsetThatWouldOverflow() {
        when(client.getPage(50, Integer.MAX_VALUE)).thenReturn(new PokeApiPage(1351, List.of()));

        PageResult<PokemonSummary> page = adapter.findPage(Integer.MAX_VALUE, 50);

        assertThat(page.items()).isEmpty();
    }

    @Test
    void listPageBeyondTheEndIsEmptyButKeepsTheTotal() {
        when(client.getPage(12, 6000)).thenReturn(new PokeApiPage(1351, List.of()));

        PageResult<PokemonSummary> page = adapter.findPage(500, 12);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isEqualTo(1351);
        verify(client, never()).getPokemon(anyString());
    }

    @Test
    void listFailsAsAWholeWhenAnyDetailFails() {
        when(client.getPage(3, 0)).thenReturn(Fixtures.page("list.json"));
        when(client.getPokemon("1")).thenReturn(Optional.of(named(1, "bulbasaur")));
        when(client.getPokemon("2")).thenThrow(new CatalogUnavailableException("boom", true, null));
        when(client.getPokemon("3")).thenReturn(Optional.of(named(3, "venusaur")));

        assertThatThrownBy(() -> adapter.findPage(0, 3))
                .isInstanceOfSatisfying(CatalogUnavailableException.class, e -> assertThat(e.isTimeout()).isTrue());
    }

    @Test
    void listFailsWhenAListedPokemonCannotBeFetched() {
        when(client.getPage(3, 0)).thenReturn(Fixtures.page("list.json"));
        when(client.getPokemon(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.findPage(0, 3)).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void detailCombinesPokemonSpeciesAndEvolutionChain() {
        when(client.getPokemon("25")).thenReturn(Optional.of(Fixtures.pokemon("pokemon-25.json")));
        when(client.getSpecies(25)).thenReturn(Optional.of(Fixtures.species("species-25.json")));
        when(client.getEvolutionChain(10)).thenReturn(Optional.of(Fixtures.chain("evolution-10.json")));

        PokemonDetail detail = adapter.findDetail("25").orElseThrow();

        assertThat(detail.name()).isEqualTo("pikachu");
        assertThat(detail.genus()).isEqualTo("Mouse Pokémon");
        assertThat(detail.description()).startsWith("When several of these");
        assertThat(detail.evolutionChain().name()).isEqualTo("pichu");
    }

    @Test
    void detailResolvesTheSpeciesFromThePayloadNotFromThePokemonId() {
        when(client.getPokemon("deoxys-attack")).thenReturn(Optional.of(Fixtures.pokemon("pokemon-10001.json")));
        when(client.getSpecies(386)).thenReturn(Optional.empty());

        PokemonDetail detail = adapter.findDetail("deoxys-attack").orElseThrow();

        assertThat(detail.id()).isEqualTo(10001);
        verify(client).getSpecies(386);
        verify(client, never()).getSpecies(10001);
    }

    @Test
    void detailToleratesMissingSpeciesData() {
        when(client.getPokemon("25")).thenReturn(Optional.of(Fixtures.pokemon("pokemon-25.json")));
        when(client.getSpecies(anyInt())).thenReturn(Optional.empty());

        PokemonDetail detail = adapter.findDetail("25").orElseThrow();

        assertThat(detail.description()).isNull();
        assertThat(detail.genus()).isNull();
        assertThat(detail.evolutionChain()).isNull();
        verify(client, never()).getEvolutionChain(anyInt());
    }

    @Test
    void detailIsEmptyWhenThePokemonDoesNotExist() {
        when(client.getPokemon("missingno")).thenReturn(Optional.empty());

        assertThat(adapter.findDetail("missingno")).isEmpty();
        verify(client, never()).getSpecies(anyInt());
    }

    private static PokeApiPage index(String... namesWithIds) {
        List<PokeApiPage.Entry> entries = java.util.Arrays.stream(namesWithIds)
                .map(item -> item.split(":"))
                .map(parts -> new PokeApiPage.Entry(parts[1], "https://pokeapi.co/api/v2/pokemon/" + parts[0] + "/"))
                .toList();
        return new PokeApiPage(entries.size(), entries);
    }

    private void stubDetails(int... ids) {
        for (int id : ids) {
            when(client.getPokemon(String.valueOf(id))).thenReturn(Optional.of(named(id, "n" + id)));
        }
    }

    private static PokemonSearchQuery query(String raw) {
        return PokemonSearchQuery.of(raw).orElseThrow();
    }

    @Test
    void searchByNameFiltersOrdersByIdAndFetchesOnlyTheRequestedPage() {
        when(client.getNameIndex()).thenReturn(index("3:venusaur", "25:pikachu", "1:bulbasaur", "2:ivysaur"));
        stubDetails(1, 2);

        PageResult<PokemonSummary> result = adapter.search(query("SAUR"), 0, 2);

        assertThat(result.items()).extracting(PokemonSummary::id).containsExactly(1, 2);
        assertThat(result.totalItems()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(2);
        verify(client, never()).getPokemon("3");
        verify(client, never()).getPokemon("25");
    }

    @Test
    void searchSecondPageHoldsTheRemainingMatches() {
        when(client.getNameIndex()).thenReturn(index("3:venusaur", "1:bulbasaur", "2:ivysaur"));
        stubDetails(3);

        PageResult<PokemonSummary> result = adapter.search(query("saur"), 1, 2);

        assertThat(result.items()).extracting(PokemonSummary::id).containsExactly(3);
        assertThat(result.totalItems()).isEqualTo(3);
    }

    @Test
    void searchByIdMatchesOnlyThatPokemon() {
        when(client.getNameIndex()).thenReturn(index("1:bulbasaur", "25:pikachu", "250:ho-oh"));
        stubDetails(25);

        PageResult<PokemonSummary> result = adapter.search(query("25"), 0, 12);

        assertThat(result.items()).extracting(PokemonSummary::id).containsExactly(25);
        assertThat(result.totalItems()).isEqualTo(1);
    }

    @Test
    void searchByAnIdMissingFromTheIndexIsEmpty() {
        when(client.getNameIndex()).thenReturn(index("1:bulbasaur"));

        PageResult<PokemonSummary> result = adapter.search(query("99999"), 0, 12);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
        verify(client, never()).getPokemon(anyString());
    }

    @Test
    void searchWithoutMatchesIsAnEmptyPage() {
        when(client.getNameIndex()).thenReturn(index("1:bulbasaur"));

        PageResult<PokemonSummary> result = adapter.search(query("zzz"), 0, 12);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void searchBeyondTheLastPageKeepsTheTotalsOfTheMatches() {
        when(client.getNameIndex()).thenReturn(index("1:bulbasaur", "2:ivysaur", "3:venusaur"));

        PageResult<PokemonSummary> result = adapter.search(query("saur"), 40, 2);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isEqualTo(3);
        assertThat(result.page()).isEqualTo(40);
        verify(client, never()).getPokemon(anyString());
    }

    @Test
    void searchPageOffsetDoesNotOverflowForHugePageNumbers() {
        when(client.getNameIndex()).thenReturn(index("1:bulbasaur"));

        assertThat(adapter.search(query("bulba"), Integer.MAX_VALUE, 50).items()).isEmpty();
    }

    @Test
    void searchToleratesAnIndexWithoutResults() {
        when(client.getNameIndex()).thenReturn(new PokeApiPage(0, null));

        assertThat(adapter.search(query("a"), 0, 12).totalItems()).isZero();
    }

    @Test
    void searchPropagatesUpstreamFailures() {
        when(client.getNameIndex()).thenThrow(new CatalogUnavailableException("PokeAPI answered 503", false, null));

        assertThatThrownBy(() -> adapter.search(query("a"), 0, 12)).isInstanceOf(CatalogUnavailableException.class);
    }
}
