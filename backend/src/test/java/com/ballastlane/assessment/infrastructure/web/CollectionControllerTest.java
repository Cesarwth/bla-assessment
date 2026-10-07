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

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.ballastlane.assessment.application.port.in.DeleteStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.GetStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonIdsUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.SyncSelectedPokemonUseCase;
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
import com.ballastlane.assessment.domain.exception.InvalidSyncSelectionException;
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

    @Autowired MockMvc mockMvc;
    @Autowired TokenIssuer tokens;
    @MockitoBean ListStoredPokemonUseCase listStored;
    @MockitoBean ListStoredPokemonIdsUseCase listIds;
    @MockitoBean GetStoredPokemonUseCase getStored;
    @MockitoBean SyncPokemonUseCase syncOne;
    @MockitoBean SyncSelectedPokemonUseCase syncSelected;
    @MockitoBean UpdateStoredPokemonUseCase updateStored;
    @MockitoBean DeleteStoredPokemonUseCase deleteStored;

    private static final Actor USER_ACTOR = new Actor(1L, "someone", Role.USER);

    private String bearer(Role role) {
        return "Bearer " + tokens.issue(new User(1L, "someone", "someone@pallet.town", "hash", role)).jwt();
    }


    @Test
    void idsReturnsAPlainArrayOfTheCallersPokeApiIdsAndIsNotParsedAsAnId() throws Exception {
        when(listIds.list(USER_ACTOR)).thenReturn(List.of(1, 4, 25));

        mockMvc.perform(get("/api/v1/collection/ids").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value(1))
                .andExpect(jsonPath("$[2]").value(25));
        verifyNoInteractions(getStored);
    }

    @Test
    void idsOfAnEmptyCollectionIsAnEmptyArray() throws Exception {
        when(listIds.list(USER_ACTOR)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/collection/ids").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void idsNeedsAToken() throws Exception {
        mockMvc.perform(get("/api/v1/collection/ids")).andExpect(status().isUnauthorized());
        verifyNoInteractions(listIds);
    }

    @Test
    void listSerializesAMissingImageAsNull() throws Exception {
        StoredPokemon noImage = new StoredPokemon(STORED.id(), STORED.version(), STORED.trainerId(), 25, "pikachu", null,
                null, STORED.types(), STORED.abilities(), STORED.stats(), 6.0, 0.4, null, null, null,
                STORED.localAttributes(), STORED.syncedAt());
        when(listStored.list(null, null, USER_ACTOR)).thenReturn(new PageResult<>(List.of(noImage), 0, 12, 1));

        mockMvc.perform(get("/api/v1/collection").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].imageUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void listReturnsTheCallersEntriesWithTheirNotesAndNoOwnerId() throws Exception {
        when(listStored.list(null, null, USER_ACTOR)).thenReturn(new PageResult<>(List.of(STORED), 0, 12, 1));

        mockMvc.perform(get("/api/v1/collection").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(25))
                .andExpect(jsonPath("$.items[0].name").value("pikachu"))
                .andExpect(jsonPath("$.items[0].spriteUrl").value("sprite/25"))
                .andExpect(jsonPath("$.items[0].imageUrl").value("img/25"))
                .andExpect(jsonPath("$.items[0].nickname").value("Pika"))
                .andExpect(jsonPath("$.items[0].tags[0]").value("starter"))
                .andExpect(jsonPath("$.items[0].ownerId").doesNotExist())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void getReturnsTheFullEntryOfTheCaller() throws Exception {
        when(getStored.get(25, USER_ACTOR)).thenReturn(STORED);

        mockMvc.perform(get("/api/v1/collection/25").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.region").value("Kanto"))
                .andExpect(jsonPath("$.stats[0].name").value("hp"))
                .andExpect(jsonPath("$.evolutionChain.name").value("pikachu"))
                .andExpect(jsonPath("$.syncedAt").value("2026-10-05T12:00:00Z"));
    }

    @Test
    void get404WhenTheCallerDoesNotHaveIt() throws Exception {
        when(getStored.get(99, USER_ACTOR)).thenThrow(new StoredPokemonNotFoundException(99));

        mockMvc.perform(get("/api/v1/collection/99").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
    }

    @Test
    void get400WhenTheIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/v1/collection/abc").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void readsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/v1/collection")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/collection/25")).andExpect(status().isUnauthorized());
        verifyNoInteractions(listStored, getStored);
    }


    @Test
    void syncSingleReturns201WhenCreatedAnd200WhenRefreshed() throws Exception {
        when(syncOne.sync(eq("pikachu"), any())).thenReturn(new SyncResult(SyncOutcome.CREATED, STORED));
        when(syncOne.sync(eq("raichu"), any())).thenReturn(new SyncResult(SyncOutcome.UPDATED, STORED));

        mockMvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(25));
        mockMvc.perform(post("/api/v1/collection/raichu/sync").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk());
    }

    @Test
    void syncSelectedReturnsTheReport() throws Exception {
        when(syncSelected.sync(eq(List.of(1, 2, 3)), any())).thenReturn(new SyncReport(2, 0, List.of(new SyncReport.Failure("3", "Not found upstream"))));

        mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"ids\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.failures[0].identifier").value("3"))
                .andExpect(jsonPath("$.failures[0].reason").value("Not found upstream"))
                .andExpect(jsonPath("$.abortedReason").isEmpty());
    }

    @Test
    void syncSelectedTellsTheClientWhenTheRunWasCutShort() throws Exception {
        when(syncSelected.sync(eq(List.of(1, 4, 7)), any())).thenReturn(new SyncReport(1, 0, List.of(), "Catalog unavailable at id 2"));

        mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"ids\":[1,4,7]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.abortedReason").value("Catalog unavailable at id 2"));
    }

    @Test
    void syncRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/v1/collection/pikachu/sync")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/collection/sync").contentType(JSON).content("{\"ids\":[1,2,3]}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(syncOne, syncSelected);
    }

    @Test
    void syncSelected400WhenTheUseCaseRejectsTheSelection() throws Exception {
        when(syncSelected.sync(eq(List.of(1, 2)), any())).thenThrow(new InvalidSyncSelectionException("too many"));

        mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"ids\":[1,2]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SYNC_SELECTION"))
                .andExpect(jsonPath("$.message").value("too many"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"ids\":null}", "{\"ids\":[]}", "{\"ids\":[1,null]}", "{\"ids\":[0]}",
            "{\"ids\":[3,-1]}"})
    void syncSelected400WhenTheIdsFailTheShapeCheck(String body) throws Exception {
        mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(org.hamcrest.Matchers.startsWith("ids")));
        verifyNoInteractions(syncSelected);
    }

    @Test
    void syncSelected400WhenMoreThanFiftyIdsAreSent() throws Exception {
        String ids = IntStream.rangeClosed(1, 51).mapToObj(String::valueOf).collect(Collectors.joining(","));

        mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"ids\":[" + ids + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must contain at most 50 ids"));
        verifyNoInteractions(syncSelected);
    }

    @Test
    void syncSelected400WhenTheBodyIsNotTheExpectedShape() throws Exception {
        mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"from\":1,\"to\":3}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"ids\":[\"a\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verifyNoInteractions(syncSelected);
    }


    @Test
    void patchUpdatesTheCallersNotes() throws Exception {
        when(updateStored.update(any())).thenReturn(STORED);

        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"Pika\",\"region\":\"Kanto\",\"tags\":[\"starter\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Pika"));

        verify(updateStored).update(new UpdateStoredPokemonCommand(25, 2L, "Pika", "Kanto", List.of("starter"), USER_ACTOR));
    }

    @Test
    void patchPassesAbsentFieldsAsNullSoTheyStayUnchanged() throws Exception {
        when(updateStored.update(any())).thenReturn(STORED);

        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"region\":\"Johto\"}"))
                .andExpect(status().isOk());

        verify(updateStored).update(new UpdateStoredPokemonCommand(25, 2L, null, "Johto", null, USER_ACTOR));
    }

    @Test
    void patch400WhenThereIsNothingToUpdate() throws Exception {
        when(updateStored.update(any())).thenThrow(new InvalidStoredPokemonUpdateException("nothing"));

        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPDATE"));
    }

    @Test
    void patch400WhenAttributesBreakTheRules() throws Exception {
        when(updateStored.update(any())).thenThrow(new InvalidLocalAttributesException("nickname too long"));

        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"" + "x".repeat(80) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOCAL_ATTRIBUTES"));
    }

    @Test
    void patch400OnMalformedJsonOrWrongTypes() throws Exception {
        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
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

        mockMvc.perform(patch("/api/v1/collection/99").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void patchRequiresAToken() throws Exception {
        mockMvc.perform(patch("/api/v1/collection/25").contentType(JSON).content("{\"version\":2,\"nickname\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(updateStored);
    }


    @Test
    void deleteReturns204ForAnyAuthenticatedTrainerAndUsesTheActorFromTheToken() throws Exception {
        mockMvc.perform(delete("/api/v1/collection/25").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isNoContent());

        verify(deleteStored).delete(25, USER_ACTOR);
    }

    @Test
    void deleteReturns401WhenAnonymous() throws Exception {
        mockMvc.perform(delete("/api/v1/collection/25")).andExpect(status().isUnauthorized());
        verifyNoInteractions(deleteStored);
    }

    @Test
    void delete404WhenTheCallerDoesNotHaveIt() throws Exception {
        doThrow(new StoredPokemonNotFoundException(99)).when(deleteStored).delete(org.mockito.ArgumentMatchers.eq(99), any());

        mockMvc.perform(delete("/api/v1/collection/99").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
    }


    @Test
    void syncPassesTheActorBuiltFromTheTokenNotFromTheRequest() throws Exception {
        when(syncOne.sync(eq("pikachu"), any())).thenReturn(new SyncResult(SyncOutcome.CREATED, STORED));
        when(syncSelected.sync(eq(List.of(1, 2)), any())).thenReturn(new SyncReport(2, 0, List.of()));

        mockMvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/collection/sync").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk());

        verify(syncOne).sync("pikachu", new Actor(1L, "someone", Role.ADMIN));
        verify(syncSelected).sync(List.of(1, 2), USER_ACTOR);
    }

    @Test
    void patchRequiresTheVersionTheClientRead() throws Exception {
        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"nickname\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
        verifyNoInteractions(updateStored);
    }

    @Test
    void syncAndPatchResponsesDoNotExposeAnOwnerId() throws Exception {
        when(syncOne.sync(eq("pikachu"), any())).thenReturn(new SyncResult(SyncOutcome.CREATED, STORED));
        when(updateStored.update(any())).thenReturn(STORED);

        mockMvc.perform(post("/api/v1/collection/pikachu/sync").header("Authorization", bearer(Role.USER)))
                .andExpect(jsonPath("$.ownerId").doesNotExist());
        mockMvc.perform(patch("/api/v1/collection/25").header("Authorization", bearer(Role.USER))
                        .contentType(JSON).content("{\"version\":2,\"nickname\":\"x\"}"))
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }
}
