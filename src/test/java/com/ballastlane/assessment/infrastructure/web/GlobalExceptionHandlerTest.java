package com.ballastlane.assessment.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.InvalidPaginationException;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.InvalidCredentialsException;
import com.ballastlane.assessment.domain.exception.InvalidLocalAttributesException;
import com.ballastlane.assessment.domain.exception.InvalidStoredPokemonUpdateException;
import com.ballastlane.assessment.domain.exception.InvalidSyncRangeException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.InvalidUserException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.WeakPasswordException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsDomainExceptionsToStatusAndStableCode() throws Exception {
        mvc.perform(get("/boom/invalid-user")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/boom/weak-password")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        mvc.perform(get("/boom/username-taken")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mvc.perform(get("/boom/email-taken")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        mvc.perform(get("/boom/bad-credentials")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void mapsCatalogExceptionsToStatusAndStableCode() throws Exception {
        mvc.perform(get("/boom/pokemon-missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POKEMON_NOT_FOUND"));
        mvc.perform(get("/boom/bad-pagination")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mvc.perform(get("/boom/bad-identifier")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_POKEMON_IDENTIFIER"));
        mvc.perform(get("/boom/catalog-down")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
        mvc.perform(get("/boom/catalog-timeout")).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("CATALOG_TIMEOUT"));
    }

    @Test
    void mapsCollectionExceptionsToStatusAndStableCode() throws Exception {
        mvc.perform(get("/boom/stored-missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
        mvc.perform(get("/boom/bad-attributes")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOCAL_ATTRIBUTES"));
        mvc.perform(get("/boom/bad-update")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPDATE"));
        mvc.perform(get("/boom/bad-range")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SYNC_RANGE"));
        mvc.perform(get("/boom/stale")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void returns400WhenARequestParameterHasTheWrongType() throws Exception {
        mvc.perform(get("/boom/typed").param("n", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void returnsFieldErrorsOnBeanValidationFailure() throws Exception {
        mvc.perform(post("/boom/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returns400OnMalformedJson() throws Exception {
        mvc.perform(post("/boom/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void keepsFrameworkStatusCodes() throws Exception {
        mvc.perform(post("/boom/invalid-user")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void neverLeaksInternalsOnUnexpectedErrors() throws Exception {
        mvc.perform(get("/boom/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected error"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    record Payload(@NotBlank String name) {
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/boom/invalid-user") void a() { throw new InvalidUserException("bad"); }
        @GetMapping("/boom/weak-password") void b() { throw new WeakPasswordException("weak"); }
        @GetMapping("/boom/username-taken") void c() { throw new UsernameAlreadyExistsException("ash"); }
        @GetMapping("/boom/email-taken") void d() { throw new EmailAlreadyExistsException("a@b.co"); }
        @GetMapping("/boom/bad-credentials") void e() { throw new InvalidCredentialsException(); }
        @GetMapping("/boom/unexpected") void f() { throw new IllegalStateException("secret internals"); }
        @GetMapping("/boom/pokemon-missing") void h() { throw new PokemonNotFoundException("x"); }
        @GetMapping("/boom/bad-pagination") void i() { throw new InvalidPaginationException("bad"); }
        @GetMapping("/boom/bad-identifier") void j() { throw new InvalidPokemonIdentifierException("bad"); }
        @GetMapping("/boom/catalog-down") void k() { throw new CatalogUnavailableException("down", false, null); }
        @GetMapping("/boom/catalog-timeout") void l() { throw new CatalogUnavailableException("slow", true, null); }
        @GetMapping("/boom/stored-missing") void n() { throw new StoredPokemonNotFoundException(25); }
        @GetMapping("/boom/bad-attributes") void o() { throw new InvalidLocalAttributesException("bad"); }
        @GetMapping("/boom/bad-update") void q() { throw new InvalidStoredPokemonUpdateException("bad"); }
        @GetMapping("/boom/bad-range") void r() { throw new InvalidSyncRangeException("bad"); }
        @GetMapping("/boom/stale") void t() { throw new StaleStoredPokemonException(25); }
        @GetMapping("/boom/typed") void m(@RequestParam int n) { }
        @PostMapping("/boom/validate") void g(@Valid @RequestBody Payload payload) { }
    }
}
