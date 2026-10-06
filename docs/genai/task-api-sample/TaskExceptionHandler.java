package com.ballastlane.assessment.genai.tasks;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** One error shape for every failure: {status, code, message, fieldErrors}. */
@RestControllerAdvice
public class TaskExceptionHandler {

    record FieldProblem(String field, String message) {
    }

    record ErrorBody(int status, String code, String message, List<FieldProblem> fieldErrors) {
    }

    private static ResponseEntity<ErrorBody> body(HttpStatus status, String code, String message, List<FieldProblem> fields) {
        return ResponseEntity.status(status).body(new ErrorBody(status.value(), code, message, fields));
    }

    @ExceptionHandler(InvalidTaskException.class)
    ResponseEntity<ErrorBody> invalid(InvalidTaskException e) {
        return body(HttpStatus.BAD_REQUEST, "INVALID_TASK", e.getMessage(), List.of());
    }

    @ExceptionHandler(TaskNotFoundException.class)
    ResponseEntity<ErrorBody> notFound(TaskNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", e.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ResponseEntity<ErrorBody> transition(InvalidTransitionException e) {
        return body(HttpStatus.CONFLICT, "INVALID_TRANSITION", e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e) {
        List<FieldProblem> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldProblem(f.getField(), f.getDefaultMessage())).toList();
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> malformed(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or malformed", List.of());
    }
}
