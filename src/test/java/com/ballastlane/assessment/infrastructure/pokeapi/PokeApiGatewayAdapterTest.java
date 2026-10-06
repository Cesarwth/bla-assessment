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
import com.ballastlane.assessment.domain.PokemonSummary;
import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPage;
import com.ballastlane.assessment.infrastructure.pokeapi.dto.PokeApiPokemon;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    void listKeepsPageOrderEvenWhenEarlierCallsAreSlower() {
        when(client.getPage(3, 0)).thenReturn(Fixtures.page("list.json"));
        when(client.getPokemon("1")).thenAnswer(inv -> {
            Thread.sleep(150);
            return Optional.of(named(1, "bulbasaur"));
        });
        when(client.getPokemon("2")).thenReturn(Optional.of(named(2, "ivysaur")));
        when(client.getPokemon("3")).thenReturn(Optional.of(named(3, "venusaur")));

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
}
