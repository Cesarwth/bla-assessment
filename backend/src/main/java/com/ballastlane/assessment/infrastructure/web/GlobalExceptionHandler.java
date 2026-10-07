package com.ballastlane.assessment.infrastructure.web;

import com.ballastlane.assessment.domain.exception.CatalogUnavailableException;
import com.ballastlane.assessment.domain.exception.DuplicateStoredPokemonException;
import com.ballastlane.assessment.domain.exception.EmailAlreadyExistsException;
import com.ballastlane.assessment.domain.exception.InvalidLocalAttributesException;
import com.ballastlane.assessment.domain.exception.InvalidPaginationException;
import com.ballastlane.assessment.domain.exception.InvalidPokemonIdentifierException;
import com.ballastlane.assessment.domain.exception.InvalidSearchQueryException;
import com.ballastlane.assessment.domain.exception.InvalidStoredPokemonUpdateException;
import com.ballastlane.assessment.domain.exception.InvalidSyncSelectionException;
import com.ballastlane.assessment.domain.exception.PokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.StaleStoredPokemonException;
import com.ballastlane.assessment.domain.exception.StoredPokemonNotFoundException;
import com.ballastlane.assessment.domain.exception.InvalidCredentialsException;
import com.ballastlane.assessment.domain.exception.InvalidCurrentPasswordException;
import com.ballastlane.assessment.domain.exception.InvalidResetTokenException;
import com.ballastlane.assessment.domain.exception.UserNotFoundException;
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
    ResponseEntity<ErrorResponse> invalidUser(InvalidUserException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(WeakPasswordException.class)
    ResponseEntity<ErrorResponse> weakPassword(WeakPasswordException exception) {
        return respond(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", exception.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> usernameTaken(UsernameAlreadyExistsException exception) {
        return respond(HttpStatus.CONFLICT, "USERNAME_TAKEN", exception.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> emailTaken(EmailAlreadyExistsException exception) {
        return respond(HttpStatus.CONFLICT, "EMAIL_TAKEN", exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> invalidCredentials(InvalidCredentialsException exception) {
        // Neither the username nor the password: a failed attempt is worth counting, not worth identifying.
        log.warn("Login failed");
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage());
    }

    // 400, not 401: the web client treats every 401 as "session expired" and would log the user out for a typo.
    @ExceptionHandler(InvalidCurrentPasswordException.class)
    ResponseEntity<ErrorResponse> invalidCurrentPassword(InvalidCurrentPasswordException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", exception.getMessage());
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    ResponseEntity<ErrorResponse> invalidResetToken(InvalidResetTokenException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", exception.getMessage());
    }

    // A valid token for a deleted account: the session is no longer good for anything.
    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ErrorResponse> userNotFound(UserNotFoundException exception) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
    }

    @ExceptionHandler(PokemonNotFoundException.class)
    ResponseEntity<ErrorResponse> pokemonNotFound(PokemonNotFoundException exception) {
        return respond(HttpStatus.NOT_FOUND, "POKEMON_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(StoredPokemonNotFoundException.class)
    ResponseEntity<ErrorResponse> storedPokemonNotFound(StoredPokemonNotFoundException exception) {
        return respond(HttpStatus.NOT_FOUND, "STORED_POKEMON_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidLocalAttributesException.class)
    ResponseEntity<ErrorResponse> invalidLocalAttributes(InvalidLocalAttributesException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_LOCAL_ATTRIBUTES", exception.getMessage());
    }

    @ExceptionHandler(InvalidStoredPokemonUpdateException.class)
    ResponseEntity<ErrorResponse> invalidUpdate(InvalidStoredPokemonUpdateException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_UPDATE", exception.getMessage());
    }

    @ExceptionHandler(InvalidSyncSelectionException.class)
    ResponseEntity<ErrorResponse> invalidSyncSelection(InvalidSyncSelectionException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_SYNC_SELECTION", exception.getMessage());
    }

    @ExceptionHandler(DuplicateStoredPokemonException.class)
    ResponseEntity<ErrorResponse> duplicateStoredPokemon(DuplicateStoredPokemonException exception) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", "Resource already exists");
    }

    // Raised inside a controller (e.g. a token that does not identify a user): without this the catch-all makes it a 500.
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> authentication(AuthenticationException exception) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
    }

    @ExceptionHandler(StaleStoredPokemonException.class)
    ResponseEntity<ErrorResponse> staleRecord(StaleStoredPokemonException exception) {
        return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The record was modified by someone else; reload it and try again");
    }

    @ExceptionHandler(InvalidPaginationException.class)
    ResponseEntity<ErrorResponse> invalidPagination(InvalidPaginationException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", exception.getMessage());
    }

    @ExceptionHandler(InvalidSearchQueryException.class)
    ResponseEntity<ErrorResponse> invalidSearchQuery(InvalidSearchQueryException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_QUERY", exception.getMessage());
    }

    @ExceptionHandler(InvalidPokemonIdentifierException.class)
    ResponseEntity<ErrorResponse> invalidIdentifier(InvalidPokemonIdentifierException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_POKEMON_IDENTIFIER", exception.getMessage());
    }

    // The upstream's own message stays in the logs: clients only learn that the catalog is unavailable.
    @ExceptionHandler(CatalogUnavailableException.class)
    ResponseEntity<ErrorResponse> catalogUnavailable(CatalogUnavailableException exception) {
        // Expected failure, so no stack trace: the message carries the upstream status, the cause class tells timeout from refusal.
        String cause = exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName();
        log.warn("Pokemon catalog unavailable (timeout={}, cause={}): {}", exception.isTimeout(), cause, exception.getMessage());
        return exception.isTimeout()
                ? respond(HttpStatus.GATEWAY_TIMEOUT, "CATALOG_TIMEOUT", "The Pokemon catalog took too long to answer")
                : respond(HttpStatus.BAD_GATEWAY, "CATALOG_UNAVAILABLE", "The Pokemon catalog is temporarily unavailable");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parameter '" + exception.getName() + "' has an invalid value");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        // One message per field keeps the response readable when several constraints fail on the same field.
        Map<String, String> firstPerField = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> firstPerField.putIfAbsent(error.getField(), error.getDefaultMessage()));
        List<FieldViolation> violations = firstPerField.entrySet().stream()
                .map(entry -> new FieldViolation(entry.getKey(), entry.getValue()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "VALIDATION_FAILED", "Request validation failed", violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException exception) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed");
    }

    // Race between the existence check and the insert: the DB constraint is the last line of defense.
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException exception) {
        // The database message quotes the offending values (e.g. an email): log the kind of error, not its text.
        log.warn("Data integrity violation rejected with 409 ({})", exception.getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "Resource already exists");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        // Spring MVC exceptions (404, 405, 415...) already know their status: keep it, don't turn it into a 500.
        if (exception instanceof org.springframework.web.ErrorResponse frameworkError) {
            HttpStatusCode code = frameworkError.getStatusCode();
            HttpStatus status = HttpStatus.resolve(code.value());
            String name = status != null ? status.name() : "ERROR";
            String reason = status != null ? status.getReasonPhrase() : "Error";
            return ResponseEntity.status(code).headers(frameworkError.getHeaders())
                    .body(ErrorResponse.of(code.value(), name, reason));
        }
        log.error("Unhandled exception", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error");
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), code, message));
    }
}
