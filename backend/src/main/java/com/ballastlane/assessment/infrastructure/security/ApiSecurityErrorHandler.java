package com.ballastlane.assessment.infrastructure.security;

import com.ballastlane.assessment.infrastructure.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/** Security filters run before MVC, so they need their own way to emit the shared error body. */
class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json;

    ApiSecurityErrorHandler(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do this");
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getWriter(), ErrorResponse.of(status.value(), code, message));
    }
}
