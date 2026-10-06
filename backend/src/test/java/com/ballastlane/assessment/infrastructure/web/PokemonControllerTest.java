package com.ballastlane.assessment.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.application.port.in.GetPokemonDetailUseCase;
import com.ballastlane.assessment.application.port.in.ListPokemonUseCase;
import com.ballastlane.assessment.domain.EvolutionNode;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.PokemonDetail;
import com.ballastlane.assessment.domain.PokemonStat;
import com.ballastlane.assessment.domain.PokemonSummary;
import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.domain.exception.InvalidPaginationException;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.infrastructure.security.JwtConfig;
import com.ballastlane.assessment.infrastructure.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PokemonController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class})
class PokemonControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ListPokemonUseCase listPokemon;
    @MockitoBean GetPokemonDetailUseCase getPokemonDetail;

    private static final PokemonSummary PIKACHU = new PokemonSummary(25, "pikachu", "https://img/25.png",
            List.of("electric"), 6.0, List.of("static", "lightning-rod"));

    @Test
    void listReturnsPagedSummariesWithoutAToken() throws Exception {
        when(listPokemon.list(1, 1)).thenReturn(new PageResult<>(List.of(PIKACHU), 1, 1, 1351));

        mvc.perform(get("/api/v1/pokemon").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=60")))
                .andExpect(jsonPath("$.items[0].id").value(25))
                .andExpect(jsonPath("$.items[0].name").value("pikachu"))
                .andExpect(jsonPath("$.items[0].spriteUrl").value("https://img/25.png"))
                .andExpect(jsonPath("$.items[0].types[0]").value("electric"))
                .andExpect(jsonPath("$.items[0].weightKg").value(6.0))
                .andExpect(jsonPath("$.items[0].abilities[1]").value("lightning-rod"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalItems").value(1351))
                .andExpect(jsonPath("$.totalPages").value(1351));
    }

    @Test
    void listLeavesDefaultsToTheUseCaseWhenParametersAreMissing() throws Exception {
        when(listPokemon.list(null, null)).thenReturn(new PageResult<>(List.of(), 0, 12, 0));

        mvc.perform(get("/api/v1/pokemon")).andExpect(status().isOk());

        verify(listPokemon).list(null, null);
    }

    @Test
    void list400WhenPaginationIsInvalid() throws Exception {
        when(listPokemon.list(any(), any())).thenThrow(new InvalidPaginationException("size must be between 1 and 50"));

        mvc.perform(get("/api/v1/pokemon").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"))
                // Errors must never inherit the 60s public cache that successful reads get.
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("max-age=60"))));
    }

    @Test
    void list400WhenAParameterIsNotANumber() throws Exception {
        mvc.perform(get("/api/v1/pokemon").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Parameter 'size' has an invalid value"));
    }

    @Test
    void detailReturnsEverythingTheDetailViewNeeds() throws Exception {
        EvolutionNode raichu = new EvolutionNode(26, "raichu", "r.png", List.of("Use thunder-stone"), List.of());
        EvolutionNode pikachu = new EvolutionNode(25, "pikachu", "p.png", List.of("Level up (happiness 220)"), List.of(raichu));
        PokemonDetail detail = new PokemonDetail(25, "pikachu", "https://img/art.png", "https://img/25.png",
                List.of("electric"), 6.0, 0.4, List.of("static"), List.of(new PokemonStat("hp", 35)),
                "Mouse Pokémon", "When several of these POKéMON gather...", pikachu);
        when(getPokemonDetail.get("25")).thenReturn(detail);

        mvc.perform(get("/api/v1/pokemon/25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("pikachu"))
                .andExpect(jsonPath("$.imageUrl").value("https://img/art.png"))
                .andExpect(jsonPath("$.heightM").value(0.4))
                .andExpect(jsonPath("$.stats[0].name").value("hp"))
                .andExpect(jsonPath("$.stats[0].baseValue").value(35))
                .andExpect(jsonPath("$.genus").value("Mouse Pokémon"))
                .andExpect(jsonPath("$.description").value("When several of these POKéMON gather..."))
                .andExpect(jsonPath("$.evolutionChain.name").value("pikachu"))
                .andExpect(jsonPath("$.evolutionChain.requirements[0]").value("Level up (happiness 220)"))
                .andExpect(jsonPath("$.evolutionChain.evolvesTo[0].name").value("raichu"));
    }

    @Test
    void detailKeepsNullEvolutionChainAsNull() throws Exception {
        when(getPokemonDetail.get("25")).thenReturn(new PokemonDetail(25, "pikachu", null, null, List.of(), 6.0, 0.4,
                List.of(), List.of(), null, null, null));

        mvc.perform(get("/api/v1/pokemon/25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evolutionChain").isEmpty())
                .andExpect(jsonPath("$.imageUrl").isEmpty());
    }

    @Test
    void detail404WhenThePokemonDoesNotExist() throws Exception {
        when(getPokemonDetail.get("missingno")).thenThrow(new PokemonNotFoundException("missingno"));

        mvc.perform(get("/api/v1/pokemon/missingno"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POKEMON_NOT_FOUND"));
    }

    @Test
    void detail400WhenTheIdentifierIsInvalid() throws Exception {
        when(getPokemonDetail.get("bad!")).thenThrow(new InvalidPokemonIdentifierException("not valid"));

        mvc.perform(get("/api/v1/pokemon/bad!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_POKEMON_IDENTIFIER"));
    }

    @Test
    void detail502WhenTheCatalogIsDownWithoutLeakingUpstreamDetails() throws Exception {
        when(getPokemonDetail.get("25")).thenThrow(new CatalogUnavailableException("PokeAPI answered 503", false, null));

        mvc.perform(get("/api/v1/pokemon/25"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("The Pokemon catalog is temporarily unavailable"));
    }

    @Test
    void detail504WhenTheCatalogTimesOut() throws Exception {
        when(getPokemonDetail.get("25")).thenThrow(new CatalogUnavailableException("timeout", true, null));

        mvc.perform(get("/api/v1/pokemon/25"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("CATALOG_TIMEOUT"));
    }
}
