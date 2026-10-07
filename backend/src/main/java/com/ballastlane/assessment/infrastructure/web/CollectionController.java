package com.ballastlane.assessment.infrastructure.web;

import com.ballastlane.assessment.application.port.in.DeleteStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.GetStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonIdsUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.SyncSelectedPokemonUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonCommand;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonUseCase;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.SyncOutcome;
import com.ballastlane.assessment.domain.SyncReport;
import com.ballastlane.assessment.domain.SyncResult;
import com.ballastlane.assessment.infrastructure.web.dto.PageResponse;
import com.ballastlane.assessment.infrastructure.web.dto.StoredPokemonResponse;
import com.ballastlane.assessment.infrastructure.web.dto.StoredPokemonSummaryResponse;
import com.ballastlane.assessment.infrastructure.web.dto.SyncSelectionRequest;
import com.ballastlane.assessment.infrastructure.web.dto.SyncReportResponse;
import com.ballastlane.assessment.infrastructure.web.dto.UpdatePokemonRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

/** The authenticated trainer's own collection: every route needs a token and only ever touches that trainer's entries. */
@RestController
@RequestMapping("/api/v1/collection")
public class CollectionController {

    private static final Logger log = LoggerFactory.getLogger(CollectionController.class);

    private final ListStoredPokemonUseCase listStored;
    private final ListStoredPokemonIdsUseCase listIds;
    private final GetStoredPokemonUseCase getStored;
    private final SyncPokemonUseCase syncOne;
    private final SyncSelectedPokemonUseCase syncSelected;
    private final UpdateStoredPokemonUseCase updateStored;
    private final DeleteStoredPokemonUseCase deleteStored;

    public CollectionController(
            ListStoredPokemonUseCase listStored,
            ListStoredPokemonIdsUseCase listIds,
            GetStoredPokemonUseCase getStored,
            SyncPokemonUseCase syncOne,
            SyncSelectedPokemonUseCase syncSelected,
            UpdateStoredPokemonUseCase updateStored,
            DeleteStoredPokemonUseCase deleteStored) {
        this.listStored = listStored;
        this.listIds = listIds;
        this.getStored = getStored;
        this.syncOne = syncOne;
        this.syncSelected = syncSelected;
        this.updateStored = updateStored;
        this.deleteStored = deleteStored;
    }

    @GetMapping
    public PageResponse<StoredPokemonSummaryResponse> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal Jwt jwt) {
        return PageResponse.from(listStored.list(page, size, ActorResolver.from(jwt)), StoredPokemonSummaryResponse::from);
    }

    // A literal path wins over /{pokeApiId}, so "ids" is never parsed as a number.
    @GetMapping("/ids")
    public List<Integer> ids(@AuthenticationPrincipal Jwt jwt) {
        return listIds.list(ActorResolver.from(jwt));
    }

    @GetMapping("/{pokeApiId}")
    public StoredPokemonResponse get(@PathVariable int pokeApiId, @AuthenticationPrincipal Jwt jwt) {
        return StoredPokemonResponse.from(getStored.get(pokeApiId, ActorResolver.from(jwt)));
    }

    @PostMapping("/{idOrName}/sync")
    public ResponseEntity<StoredPokemonResponse> sync(@PathVariable String idOrName, @AuthenticationPrincipal Jwt jwt) {
        Actor actor = ActorResolver.from(jwt);
        SyncResult syncResult = syncOne.sync(idOrName, actor);
        if (syncResult.outcome() == SyncOutcome.CREATED) {
            log.info("Trainer {} added Pokemon {}", actor.userId(), syncResult.pokemon().pokeApiId());
        }
        HttpStatus status = syncResult.outcome() == SyncOutcome.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(StoredPokemonResponse.from(syncResult.pokemon()));
    }

    @PostMapping("/sync")
    public SyncReportResponse syncSelected(@Valid @RequestBody SyncSelectionRequest request, @AuthenticationPrincipal Jwt jwt) {
        Actor actor = ActorResolver.from(jwt);
        SyncReport report = syncSelected.sync(request.ids(), actor);
        log.info("Trainer {} bulk add: requested={} created={} updated={} failed={} aborted={}", actor.userId(),
                request.ids().size(), report.created(), report.updated(), report.failures().size(),
                report.abortedReason() == null ? "no" : report.abortedReason());
        return SyncReportResponse.from(report);
    }

    @PatchMapping("/{pokeApiId}")
    public StoredPokemonResponse update(
            @PathVariable int pokeApiId, @Valid @RequestBody UpdatePokemonRequest request, @AuthenticationPrincipal Jwt jwt) {
        return StoredPokemonResponse.from(updateStored.update(new UpdateStoredPokemonCommand(
                pokeApiId, request.version(), request.nickname(), request.region(), request.tags(),
                ActorResolver.from(jwt))));
    }

    @DeleteMapping("/{pokeApiId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int pokeApiId, @AuthenticationPrincipal Jwt jwt) {
        Actor actor = ActorResolver.from(jwt);
        deleteStored.delete(pokeApiId, actor);
        log.info("Trainer {} removed Pokemon {}", actor.userId(), pokeApiId);
    }
}
