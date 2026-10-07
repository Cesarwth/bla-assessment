package com.ballastlane.assessment.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.support.LogCapture;
import com.ballastlane.assessment.domain.exception.DuplicateStoredPokemonException;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.InvalidPaginationException;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.InvalidSearchQueryException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.InvalidCredentialsException;
import com.ballastlane.assessment.domain.exception.InvalidLocalAttributesException;
import com.ballastlane.assessment.domain.exception.InvalidStoredPokemonUpdateException;
import com.ballastlane.assessment.domain.exception.InvalidSyncSelectionException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.InvalidCurrentPasswordException;
import com.ballastlane.assessment.domain.exception.InvalidResetTokenException;
import com.ballastlane.assessment.domain.exception.InvalidUserException;
import com.ballastlane.assessment.domain.exception.UserNotFoundException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.WeakPasswordException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
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

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsDomainExceptionsToStatusAndStableCode() throws Exception {
        mockMvc.perform(get("/boom/invalid-user")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/boom/weak-password")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        mockMvc.perform(get("/boom/username-taken")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mockMvc.perform(get("/boom/email-taken")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        mockMvc.perform(get("/boom/bad-credentials")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void mapsAccountRecoveryExceptionsToStatusAndStableCode() throws Exception {
        mockMvc.perform(get("/boom/bad-current-password")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"))
                .andExpect(jsonPath("$.message").value("The current password is incorrect"));
        mockMvc.perform(get("/boom/bad-reset-token")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"))
                .andExpect(jsonPath("$.message").value("This reset link is invalid or has expired"));
        mockMvc.perform(get("/boom/user-gone")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void mapsCatalogExceptionsToStatusAndStableCode() throws Exception {
        mockMvc.perform(get("/boom/pokemon-missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POKEMON_NOT_FOUND"));
        mockMvc.perform(get("/boom/bad-pagination")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mockMvc.perform(get("/boom/bad-query")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
        mockMvc.perform(get("/boom/bad-identifier")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_POKEMON_IDENTIFIER"));
        mockMvc.perform(get("/boom/catalog-down")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
        mockMvc.perform(get("/boom/catalog-timeout")).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("CATALOG_TIMEOUT"));
    }

    @Test
    void mapsCollectionExceptionsToStatusAndStableCode() throws Exception {
        mockMvc.perform(get("/boom/stored-missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORED_POKEMON_NOT_FOUND"));
        mockMvc.perform(get("/boom/bad-attributes")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOCAL_ATTRIBUTES"));
        mockMvc.perform(get("/boom/bad-update")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPDATE"));
        mockMvc.perform(get("/boom/bad-selection")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SYNC_SELECTION"));
        mockMvc.perform(get("/boom/stale")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(get("/boom/duplicate")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void integrityViolationsAre409AndNeverLogPersonalDataOrStackTraces() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get("/boom/integrity")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));

            org.assertj.core.api.Assertions.assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                org.assertj.core.api.Assertions.assertThat(event.getFormattedMessage()).doesNotContain("secret@pallet.town");
                org.assertj.core.api.Assertions.assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void anUpstreamFailureIsLoggedOnceAtWarnWithItsCauseAndNoStackTrace() throws Exception {
        try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
            mockMvc.perform(get("/boom/catalog-cause")).andExpect(status().isBadGateway());

            org.assertj.core.api.Assertions.assertThat(logs.events()).singleElement().satisfies(event -> {
                org.assertj.core.api.Assertions.assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                org.assertj.core.api.Assertions.assertThat(event.getFormattedMessage())
                        .contains("PokeAPI answered 503", "IOException", "timeout=false");
                org.assertj.core.api.Assertions.assertThat(event.getThrowableProxy()).isNull();
            });
        }
    }

    @Test
    void aFailedLoginIsLoggedAtWarnWithoutAnyCredentials() throws Exception {
        try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
            mockMvc.perform(get("/boom/bad-credentials")).andExpect(status().isUnauthorized());

            org.assertj.core.api.Assertions.assertThat(logs.events()).singleElement().satisfies(event -> {
                org.assertj.core.api.Assertions.assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                org.assertj.core.api.Assertions.assertThat(event.getFormattedMessage()).isEqualTo("Login failed");
            });
        }
    }

    @Test
    void onlyAnUnexpectedErrorIsLoggedAtErrorWithItsStackTrace() throws Exception {
        try (LogCapture logs = LogCapture.of(GlobalExceptionHandler.class)) {
            mockMvc.perform(get("/boom/unexpected")).andExpect(status().isInternalServerError());

            org.assertj.core.api.Assertions.assertThat(logs.events()).singleElement().satisfies(event -> {
                org.assertj.core.api.Assertions.assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
                org.assertj.core.api.Assertions.assertThat(event.getThrowableProxy()).isNotNull();
            });
        }
    }

    @Test
    void anAuthenticationErrorRaisedInsideTheAppIs401NotA500() throws Exception {
        mockMvc.perform(get("/boom/auth")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void returns400WhenARequestParameterHasTheWrongType() throws Exception {
        mockMvc.perform(get("/boom/typed").param("number", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void returnsFieldErrorsOnBeanValidationFailure() throws Exception {
        mockMvc.perform(post("/boom/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returns400OnMalformedJson() throws Exception {
        mockMvc.perform(post("/boom/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void keepsFrameworkStatusCodes() throws Exception {
        mockMvc.perform(post("/boom/invalid-user")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void neverLeaksInternalsOnUnexpectedErrors() throws Exception {
        mockMvc.perform(get("/boom/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected error"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    record Payload(@NotBlank String name) {
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/boom/invalid-user") void invalidUser() { throw new InvalidUserException("bad"); }
        @GetMapping("/boom/weak-password") void weakPassword() { throw new WeakPasswordException("weak"); }
        @GetMapping("/boom/username-taken") void usernameTaken() { throw new UsernameAlreadyExistsException("ash"); }
        @GetMapping("/boom/email-taken") void emailTaken() { throw new EmailAlreadyExistsException("a@b.co"); }
        @GetMapping("/boom/bad-credentials") void badCredentials() { throw new InvalidCredentialsException(); }
        @GetMapping("/boom/bad-current-password") void badCurrentPassword() { throw new InvalidCurrentPasswordException(); }
        @GetMapping("/boom/bad-reset-token") void badResetToken() { throw new InvalidResetTokenException(); }
        @GetMapping("/boom/user-gone") void userGone() { throw new UserNotFoundException(); }
        @GetMapping("/boom/unexpected") void unexpected() { throw new IllegalStateException("secret internals"); }
        @GetMapping("/boom/pokemon-missing") void pokemonMissing() { throw new PokemonNotFoundException("x"); }
        @GetMapping("/boom/bad-pagination") void badPagination() { throw new InvalidPaginationException("bad"); }
        @GetMapping("/boom/bad-query") void badQuery() { throw new InvalidSearchQueryException("q must be at most 50 characters"); }
        @GetMapping("/boom/bad-identifier") void badIdentifier() { throw new InvalidPokemonIdentifierException("bad"); }
        @GetMapping("/boom/catalog-down") void catalogDown() { throw new CatalogUnavailableException("down", false, null); }
        @GetMapping("/boom/catalog-timeout") void catalogTimeout() { throw new CatalogUnavailableException("slow", true, null); }
        @GetMapping("/boom/catalog-cause") void catalogCause() {
            throw new CatalogUnavailableException("PokeAPI answered 503", false, new java.io.IOException("upstream detail"));
        }
        @GetMapping("/boom/stored-missing") void storedMissing() { throw new StoredPokemonNotFoundException(25); }
        @GetMapping("/boom/bad-attributes") void badAttributes() { throw new InvalidLocalAttributesException("bad"); }
        @GetMapping("/boom/bad-update") void badUpdate() { throw new InvalidStoredPokemonUpdateException("bad"); }
        @GetMapping("/boom/bad-selection") void badSelection() { throw new InvalidSyncSelectionException("bad"); }
        @GetMapping("/boom/auth") void auth() { throw new BadCredentialsException("token without uid"); }
        @GetMapping("/boom/duplicate") void duplicate() { throw new DuplicateStoredPokemonException(25); }
        @GetMapping("/boom/stale") void stale() { throw new StaleStoredPokemonException(25); }
        @GetMapping("/boom/integrity") void integrity() { throw new DataIntegrityViolationException("Key (email)=(secret@pallet.town) already exists"); }
        @GetMapping("/boom/typed") void typed(@RequestParam int number) { }
        @PostMapping("/boom/validate") void validate(@Valid @RequestBody Payload payload) { }
    }
}
