package com.ballastlane.assessment.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;

/** Single error shape for every failure the API returns. */
public record ErrorResponse(Instant timestamp, int status, String code, String message, List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }

    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(Instant.now(), status, code, message, List.of());
    }

    public static ErrorResponse of(int status, String code, String message, List<FieldViolation> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, code, message, fieldErrors);
    }
}
