package com.ballastlane.assessment.infrastructure.web;

import com.ballastlane.assessment.application.port.in.GetPokemonDetailUseCase;
import com.ballastlane.assessment.application.port.in.ListPokemonUseCase;
import com.ballastlane.assessment.infrastructure.web.dto.PageResponse;
import com.ballastlane.assessment.infrastructure.web.dto.PokemonDetailResponse;
import com.ballastlane.assessment.infrastructure.web.dto.PokemonSummaryResponse;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pokemon")
public class PokemonController {

    // Upstream data is effectively static and already cached server-side; a short client cache is cheap.
    private static final CacheControl CLIENT_CACHE = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final ListPokemonUseCase listPokemon;
    private final GetPokemonDetailUseCase getPokemonDetail;

    public PokemonController(ListPokemonUseCase listPokemon, GetPokemonDetailUseCase getPokemonDetail) {
        this.listPokemon = listPokemon;
        this.getPokemonDetail = getPokemonDetail;
    }

    // Nullable on purpose: defaults and bounds are business rules owned by the use case, not by annotations here.
    @GetMapping
    public ResponseEntity<PageResponse<PokemonSummaryResponse>> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok().cacheControl(CLIENT_CACHE)
                .body(PageResponse.from(listPokemon.list(page, size), PokemonSummaryResponse::from));
    }

    @GetMapping("/{idOrName}")
    public ResponseEntity<PokemonDetailResponse> detail(@PathVariable String idOrName) {
        return ResponseEntity.ok().cacheControl(CLIENT_CACHE)
                .body(PokemonDetailResponse.from(getPokemonDetail.get(idOrName)));
    }
}
