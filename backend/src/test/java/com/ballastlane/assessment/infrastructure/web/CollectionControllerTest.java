package com.ballastlane.assessment.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.application.port.in.DeleteStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.GetStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonRangeUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonCommand;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.domain.Actor;
import com.ballastlane.assessment.domain.LocalAttributes;
import com.ballastlane.assessment.domain.PageResult;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.StoredPokemon;
import com.ballastlane.assessment.domain.SyncOutcome;
import com.ballastlane.assessment.domain.SyncReport;
import com.ballastlane.assessment.domain.SyncResult;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.domain.exception.InvalidLocalAttributesException;
import com.ballastlane.assessment.domain.exception.InvalidStoredPokemonUpdateException;
import com.ballastlane.assessment.domain.exception.InvalidSyncRangeException;
import com.ballastlane.assessment.domain.exception.NotOwnerException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;
import com.ballastlane.assessment.infrastructure.security.JwtConfig;
import com.ballastlane.assessment.infrastructure.security.SecurityConfig;
import com.ballastlane.assessment.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CollectionController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class})
class CollectionControllerTest {

    private static final StoredPokemon STORED = TestData.stored(7, 2, 25, "pikachu",
            new LocalAttributes("Pika", "Kanto", List.of("starter")));
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mvc;
    @Autowired TokenIssuer tokens;
    @MockitoBean ListStoredPokemonUseCase listStored;
    @MockitoBean GetStoredPokemonUseCase getStored;
    @MockitoBean SyncPokemonUseCase syncOne;
    @MockitoBean SyncPokemonRangeUseCase syncRange;
    @MockitoBean UpdateStoredPokemonUseCase updateStored;
    @MockitoBean DeleteStoredPokemonUseCase deleteStored;

    private static final Actor USER_ACTOR = new Actor(1L, "someone", Role.USER);

    private String bearer(Role role) {
        return "Bearer " + tokens.issue(new User(1L, "someone", "someone@pallet.town", "hash", role)).value();
    }

    // ---- reads are public ----

    @Test
    void listIsPublicAndReturnsLocalAttributesToo() throws Exception {
        when(listStored.list(null, null)).thenReturn(new PageResult<>(List.of(STORED), 0, 12, 1));

        mvc.perform(get("/api/v1/collection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(25))
                .andExpect(jsonPath("$.items[0].name").value("pikachu"))
                .andExpect(jsonPath("$.items[0].nickname").value("Pika"))
                .andExpect(jsonPath("$.items[0].tags[0]").value("starter"))
                .andExpect(jsonPath("$.items[0].ownerId").value(1))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void getIsPublicAndReturnsTheFullRecord() throws Exception {
        when(getStored.get(25)).thenReturn(STORED);

        mvc.perform(get("/api/v1/collection/25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.ownerId").value(1))
                .andExpect(jsonPath("$.region").value("Kanto"))
                .andExpect(jsonPath("$.stats[0].name").value("hp"))
                .andExpect(jsonPath("$.evolutionChain.name").value("pikachu"))
                .andExpect(jsonPath("$.syncedAt").value("2026-10-05T12:00:00Z"));
    }

    @Test
    void get404WhenMissing() throws Exception {
        when(getStored.get(99)).thenThrow(new StoredPokemonNotFoundException(99));

        mvc.perform(get("/api/v1/collection/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
    }

    @Test
    void get400WhenTheIdIsNotANumber() throws Exception {
        mvc.perform(get("/api/v1/collection/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    // ---- sync ----

    @Test
    void syncSingleReturns201WhenCreatedAnd200WhenRefreshed() throws Exception {
        when(syncOne.sync(eq("pikachu"), any())).thenReturn(new SyncResult(SyncOutcome.CREATED, STORED));
        when(syncOne.sync(eq("raichu"), any())).thenReturn(new SyncResult(SyncOutcome.UPDATED, STORED));

        mvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(25));
        mvc.perform(post("/api/v1/collection/raichu/sync").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void syncRangeReturnsTheReport() throws Exception {
        when(syncRange.sync(eq(1), eq(3), any())).thenReturn(new SyncReport(2, 0, List.of(new SyncReport.Failure("3", "Not found upstream"))));

        mvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"from\":1,\"to\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.failures[0].identifier").value("3"))
                .andExpect(jsonPath("$.failures[0].reason").value("Not found upstream"))
                .andExpect(jsonPath("$.abortedReason").isEmpty());
    }

    @Test
    void syncRangeTellsTheClientWhenTheRunWasCutShort() throws Exception {
        when(syncRange.sync(eq(1), eq(5), any())).thenReturn(new SyncReport(1, 0, List.of(), "Catalog unavailable at id 2"));

        mvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"from\":1,\"to\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.abortedReason").value("Catalog unavailable at id 2"));
    }

    @Test
    void syncRequiresAToken() throws Exception {
        mvc.perform(post("/api/v1/collection/pikachu/sync")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/collection/sync").contentType(JSON).content("{\"from\":1,\"to\":3}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(syncOne, syncRange);
    }

    @Test
    void syncRange400WhenTheRangeIsInvalidOrFieldsAreMissing() throws Exception {
        when(syncRange.sync(eq(1), eq(500), any())).thenThrow(new InvalidSyncRangeException("too wide"));

        mvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"from\":1,\"to\":500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SYNC_RANGE"));
        mvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"from\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("to"));
    }

    // ---- patch ----

    @Test
    void patchUpdatesLocalFieldsForAnyAuthenticatedUser() throws Exception {
        when(updateStored.update(any())).thenReturn(STORED);

        mvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"Pika\",\"region\":\"Kanto\",\"tags\":[\"starter\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Pika"));

        verify(updateStored).update(new UpdateStoredPokemonCommand(25, 2L, "Pika", "Kanto", List.of("starter"), USER_ACTOR));
    }

    @Test
    void patchPassesAbsentFieldsAsNullSoTheyStayUnchanged() throws Exception {
        when(updateStored.update(any())).thenReturn(STORED);

        mvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"region\":\"Johto\"}"))
                .andExpect(status().isOk());

        verify(updateStored).update(new UpdateStoredPokemonCommand(25, 2L, null, "Johto", null, USER_ACTOR));
    }

    @Test
    void patch400WhenThereIsNothingToUpdate() throws Exception {
        when(updateStored.update(any())).thenThrow(new InvalidStoredPokemonUpdateException("nothing"));

        mvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPDATE"));
    }

    @Test
    void patch400WhenAttributesBreakTheRules() throws Exception {
        when(updateStored.update(any())).thenThrow(new InvalidLocalAttributesException("nickname too long"));

        mvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"" + "x".repeat(80) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOCAL_ATTRIBUTES"));
    }

    @Test
    void patch400OnMalformedJsonOrWrongTypes() throws Exception {
        mvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"tags\":\"not-a-list\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(updateStored);
    }

    @Test
    void patch404WhenMissingAnd409OnConcurrentModification() throws Exception {
        when(updateStored.update(new UpdateStoredPokemonCommand(99, 2L, "x", null, null, USER_ACTOR)))
                .thenThrow(new StoredPokemonNotFoundException(99));
        when(updateStored.update(new UpdateStoredPokemonCommand(25, 2L, "x", null, null, USER_ACTOR)))
                .thenThrow(new StaleStoredPokemonException(25));

        mvc.perform(patch("/api/v1/collection/99").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
        mvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void patchRequiresAToken() throws Exception {
        mvc.perform(patch("/api/v1/collection/25").contentType(JSON).content("{\"version\":2,\"nickname\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(updateStored);
    }

    // ---- delete: ADMIN only ----

    @Test
    void deleteReturns204ForAnAdmin() throws Exception {
        mvc.perform(delete("/api/v1/collection/25").header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isNoContent());

        verify(deleteStored).delete(25, new Actor(1L, "someone", Role.ADMIN)); // the actor comes from the token
    }

    @Test
    void deleteReturns403ForARegularUserWithoutTouchingTheUseCase() throws Exception {
        mvc.perform(delete("/api/v1/collection/25").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(deleteStored);
    }

    @Test
    void deleteReturns401WhenAnonymous() throws Exception {
        mvc.perform(delete("/api/v1/collection/25")).andExpect(status().isUnauthorized());
        verifyNoInteractions(deleteStored);
    }

    @Test
    void delete404WhenMissing() throws Exception {
        doThrow(new StoredPokemonNotFoundException(99)).when(deleteStored).delete(org.mockito.ArgumentMatchers.eq(99), any());

        mvc.perform(delete("/api/v1/collection/99").header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
    }

    // ---- identity and ownership ----

    @Test
    void syncPassesTheActorBuiltFromTheTokenNotFromTheRequest() throws Exception {
        when(syncOne.sync(eq("pikachu"), any())).thenReturn(new SyncResult(SyncOutcome.CREATED, STORED));
        when(syncRange.sync(eq(1), eq(2), any())).thenReturn(new SyncReport(2, 0, List.of()));

        mvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"from\":1,\"to\":2}"))
                .andExpect(status().isOk());

        verify(syncOne).sync("pikachu", new Actor(1L, "someone", Role.ADMIN));
        verify(syncRange).sync(1, 2, USER_ACTOR);
    }

    @Test
    void patchRequiresTheVersionTheClientRead() throws Exception {
        mvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"nickname\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
        verifyNoInteractions(updateStored);
    }

    @Test
    void patch403WhenTheUserDoesNotOwnTheRecord() throws Exception {
        when(updateStored.update(any())).thenThrow(new NotOwnerException(25));

        mvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_NOT_OWNER"));
    }

    @Test
    void syncAndPatchResponsesExposeTheOwnerId() throws Exception {
        when(syncOne.sync(eq("pikachu"), any())).thenReturn(new SyncResult(SyncOutcome.CREATED, STORED));
        when(updateStored.update(any())).thenReturn(STORED);

        mvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", bearer(Role.USER)))
                .andExpect(jsonPath("$.ownerId").value(1));
        mvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"x\"}"))
                .andExpect(jsonPath("$.ownerId").value(1));
    }
}
