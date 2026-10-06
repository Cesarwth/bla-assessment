package com.ballastlane.assessment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApiSecurityErrorHandlerTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final ApiSecurityErrorHandler handler = new ApiSecurityErrorHandler(json);

    @Test
    void anUnauthenticatedRequestGets401WithTheSharedErrorBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response, new BadCredentialsException("secret internal detail"));

        JsonNode body = json.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("code").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("fieldErrors").size()).isZero();
        assertThat(response.getContentAsString()).doesNotContain("secret internal detail");
    }

    @Test
    void aForbiddenRequestGets403WithTheSharedErrorBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("needs ROLE_ADMIN"));

        JsonNode body = json.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(response.getContentAsString()).doesNotContain("ROLE_ADMIN");
    }
}
