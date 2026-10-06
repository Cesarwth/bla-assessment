package com.ballastlane.assessment.infrastructure.web;

import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.domain.exception.DuplicateStoredPokemonException;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.InvalidLocalAttributesException;
import com.ballastlane.assessment.domain.exception.InvalidPaginationException;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.InvalidStoredPokemonUpdateException;
import com.ballastlane.assessment.domain.exception.InvalidSyncRangeException;
import com.ballastlane.assessment.domain.exception.NotOwnerException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.InvalidCredentialsException;
import com.ballastlane.assessment.domain.exception.InvalidUserException;
import com.ballastlane.assessment.domain.exception.UsernameAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.WeakPasswordException;
import com.ballastlane.assessment.infrastructure.web.dto.ErrorResponse;
import com.ballastlane.assessment.infrastructure.web.dto.ErrorResponse.FieldViolation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidUserException.class)
    ResponseEntity<ErrorResponse> invalidUser(InvalidUserException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(WeakPasswordException.class)
    ResponseEntity<ErrorResponse> weakPassword(WeakPasswordException ex) {
        return respond(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", ex.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> usernameTaken(UsernameAlreadyExistsException ex) {
        return respond(HttpStatus.CONFLICT, "USERNAME_TAKEN", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> emailTaken(EmailAlreadyExistsException ex) {
        return respond(HttpStatus.CONFLICT, "EMAIL_TAKEN", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> invalidCredentials(InvalidCredentialsException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
    }

    @ExceptionHandler(PokemonNotFoundException.class)
    ResponseEntity<ErrorResponse> pokemonNotFound(PokemonNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "POKEMON_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(StoredPokemonNotFoundException.class)
    ResponseEntity<ErrorResponse> storedPokemonNotFound(StoredPokemonNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "STORED_POKEMON_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidLocalAttributesException.class)
    ResponseEntity<ErrorResponse> invalidLocalAttributes(InvalidLocalAttributesException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_LOCAL_ATTRIBUTES", ex.getMessage());
    }

    @ExceptionHandler(InvalidStoredPokemonUpdateException.class)
    ResponseEntity<ErrorResponse> invalidUpdate(InvalidStoredPokemonUpdateException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_UPDATE", ex.getMessage());
    }

    @ExceptionHandler(InvalidSyncRangeException.class)
    ResponseEntity<ErrorResponse> invalidSyncRange(InvalidSyncRangeException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_SYNC_RANGE", ex.getMessage());
    }

    @ExceptionHandler(DuplicateStoredPokemonException.class)
    ResponseEntity<ErrorResponse> duplicateStoredPokemon(DuplicateStoredPokemonException ex) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", "Resource already exists");
    }

    @ExceptionHandler(NotOwnerException.class)
    ResponseEntity<ErrorResponse> notOwner(NotOwnerException ex) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN_NOT_OWNER", ex.getMessage());
    }

    // Raised inside a controller (e.g. a token that does not identify a user): without this the catch-all makes it a 500.
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> authentication(AuthenticationException ex) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
    }

    @ExceptionHandler(StaleStoredPokemonException.class)
    ResponseEntity<ErrorResponse> staleRecord(StaleStoredPokemonException ex) {
        return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The record was modified by someone else; reload it and try again");
    }

    @ExceptionHandler(InvalidPaginationException.class)
    ResponseEntity<ErrorResponse> invalidPagination(InvalidPaginationException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", ex.getMessage());
    }

    @ExceptionHandler(InvalidPokemonIdentifierException.class)
    ResponseEntity<ErrorResponse> invalidIdentifier(InvalidPokemonIdentifierException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_POKEMON_IDENTIFIER", ex.getMessage());
    }

    // The upstream's own message stays in the logs: clients only learn that the catalog is unavailable.
    @ExceptionHandler(CatalogUnavailableException.class)
    ResponseEntity<ErrorResponse> catalogUnavailable(CatalogUnavailableException ex) {
        log.warn("Pokemon catalog unavailable (timeout={}): {}", ex.isTimeout(), ex.getMessage());
        return ex.isTimeout()
                ? respond(HttpStatus.GATEWAY_TIMEOUT, "CATALOG_TIMEOUT", "The Pokemon catalog took too long to answer")
                : respond(HttpStatus.BAD_GATEWAY, "CATALOG_UNAVAILABLE", "The Pokemon catalog is temporarily unavailable");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parameter '" + ex.getName() + "' has an invalid value");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
        // One message per field keeps the response readable when several constraints fail on the same field.
        Map<String, String> firstPerField = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> firstPerField.putIfAbsent(error.getField(), error.getDefaultMessage()));
        List<FieldViolation> violations = firstPerField.entrySet().stream()
                .map(entry -> new FieldViolation(entry.getKey(), entry.getValue()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_FAILED", "Request validation failed", violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed");
    }

    // Race between the existence check and the insert: the DB constraint is the last line of defense.
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException ex) {
        // The database message quotes the offending values (e.g. an email): log the kind of error, not its text.
        log.warn("Data integrity violation rejected with 409 ({})", ex.getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "Resource already exists");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        // Spring MVC exceptions (404, 405, 415...) already know their status: keep it, don't turn it into a 500.
        if (ex instanceof org.springframework.web.ErrorResponse frameworkError) {
            HttpStatusCode code = frameworkError.getStatusCode();
            HttpStatus status = HttpStatus.resolve(code.value());
            String name = status != null ? status.name() : "ERROR";
            String reason = status != null ? status.getReasonPhrase() : "Error";
            return ResponseEntity.status(code).headers(frameworkError.getHeaders())
                    .body(ErrorResponse.of(code.value(), name, reason));
        }
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error");
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), code, message));
    }
}
