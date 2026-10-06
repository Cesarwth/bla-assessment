package com.ballastlane.assessment.infrastructure.web;

import com.ballastlane.assessment.application.port.in.DeleteStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.GetStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonRangeUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonCommand;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonUseCase;
import com.ballastlane.assessment.domain.SyncOutcome;
import com.ballastlane.assessment.domain.SyncResult;
import com.ballastlane.assessment.infrastructure.web.dto.PageResponse;
import com.ballastlane.assessment.infrastructure.web.dto.StoredPokemonResponse;
import com.ballastlane.assessment.infrastructure.web.dto.StoredPokemonSummaryResponse;
import com.ballastlane.assessment.infrastructure.web.dto.SyncRangeRequest;
import com.ballastlane.assessment.infrastructure.web.dto.SyncReportResponse;
import com.ballastlane.assessment.infrastructure.web.dto.UpdatePokemonRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The editable local replica. Reads are public; writes need a token; delete is restricted in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/collection")
public class CollectionController {

    private final ListStoredPokemonUseCase listStored;
    private final GetStoredPokemonUseCase getStored;
    private final SyncPokemonUseCase syncOne;
    private final SyncPokemonRangeUseCase syncRange;
    private final UpdateStoredPokemonUseCase updateStored;
    private final DeleteStoredPokemonUseCase deleteStored;

    public CollectionController(
            ListStoredPokemonUseCase listStored,
            GetStoredPokemonUseCase getStored,
            SyncPokemonUseCase syncOne,
            SyncPokemonRangeUseCase syncRange,
            UpdateStoredPokemonUseCase updateStored,
            DeleteStoredPokemonUseCase deleteStored) {
        this.listStored = listStored;
        this.getStored = getStored;
        this.syncOne = syncOne;
        this.syncRange = syncRange;
        this.updateStored = updateStored;
        this.deleteStored = deleteStored;
    }

    @GetMapping
    public PageResponse<StoredPokemonSummaryResponse> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PageResponse.from(listStored.list(page, size), StoredPokemonSummaryResponse::from);
    }

    @GetMapping("/{pokeApiId}")
    public StoredPokemonResponse get(@PathVariable int pokeApiId) {
        return StoredPokemonResponse.from(getStored.get(pokeApiId));
    }

    @PostMapping("/{idOrName}/sync")
    public ResponseEntity<StoredPokemonResponse> sync(@PathVariable String idOrName) {
        SyncResult result = syncOne.sync(idOrName);
        HttpStatus status = result.outcome() == SyncOutcome.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(StoredPokemonResponse.from(result.pokemon()));
    }

    @PostMapping("/sync")
    public SyncReportResponse syncRange(@Valid @RequestBody SyncRangeRequest request) {
        return SyncReportResponse.from(syncRange.sync(request.from(), request.to()));
    }

    @PatchMapping("/{pokeApiId}")
    public StoredPokemonResponse update(@PathVariable int pokeApiId, @RequestBody UpdatePokemonRequest request) {
        return StoredPokemonResponse.from(updateStored.update(
                new UpdateStoredPokemonCommand(pokeApiId, request.nickname(), request.region(), request.tags())));
    }

    @DeleteMapping("/{pokeApiId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int pokeApiId) {
        deleteStored.delete(pokeApiId);
    }
}
