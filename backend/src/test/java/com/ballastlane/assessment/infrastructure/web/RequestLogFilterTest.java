package com.ballastlane.assessment.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.ballastlane.assessment.support.LogCapture;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private static FilterChain respondingWith(int status, List<String> seenInside) {
        return (request, response) -> {
            seenInside.add(MDC.get("requestId"));
            ((MockHttpServletResponse) response).setStatus(status);
        };
    }

    @Test
    void runsBeforeSpringSecurity() {
        assertThat(RequestLogFilter.class.getAnnotation(Order.class).value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void aWellFormedIncomingIdIsUsedAndEchoedInTheResponse() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/pokemon");
        request.addHeader("X-Request-Id", "abc-123_DEF.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> seen = new ArrayList<>();

        filter.doFilter(request, response, respondingWith(200, seen));

        assertThat(seen).containsExactly("abc-123_DEF.9");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123_DEF.9");
    }

    @Test
    void aMissingIdIsReplacedByAGeneratedUuid() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> seen = new ArrayList<>();

        filter.doFilter(request("GET", "/api/v1/pokemon"), response, respondingWith(200, seen));

        assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(seen).containsExactly(response.getHeader("X-Request-Id"));
    }

    @Test
    void anIncomingIdThatCouldInjectLogLinesIsIgnored() throws Exception {
        for (String hostile : List.of("a\r\nFAKE 200 ok", "has space", "semi;colon", "x".repeat(65), "")) {
            MockHttpServletRequest request = request("GET", "/api/v1/pokemon");
            request.addHeader("X-Request-Id", hostile);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, respondingWith(200, new ArrayList<>()));

            assertThat(response.getHeader("X-Request-Id")).isNotEqualTo(hostile).hasSizeBetween(1, 64);
        }
    }

    @Test
    void theMdcIsClearedAfterTheRequestEvenWhenTheChainThrows() {
        FilterChain failing = (request, response) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request("GET", "/x"), new MockHttpServletResponse(), failing))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void aFailedRequestIsLoggedAsAServerErrorAtWarn() {
        FilterChain failing = (request, response) -> {
            throw new ServletException("boom");
        };
        try (LogCapture logs = LogCapture.of(RequestLogFilter.class)) {
            assertThatThrownBy(() -> filter.doFilter(request("GET", "/x"), new MockHttpServletResponse(), failing))
                    .isInstanceOf(ServletException.class);

            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).startsWith("GET /x 500 ");
            });
        }
    }

    @Test
    void logsOneInfoLineWithMethodPathStatusAndDurationAndNeverTheQueryString() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/pokemon");
        request.setQueryString("q=pikachu&token=secret");
        request.addHeader("Authorization", "Bearer secret-jwt");
        try (LogCapture logs = LogCapture.of(RequestLogFilter.class)) {
            filter.doFilter(request, new MockHttpServletResponse(), respondingWith(200, new ArrayList<>()));

            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getFormattedMessage()).matches("GET /api/v1/pokemon 200 \\d+ms");
                assertThat(event.getMDCPropertyMap()).containsKey("requestId");
            });
        }
    }

    @Test
    void serverErrorsAreLoggedAtWarn() throws Exception {
        try (LogCapture logs = LogCapture.of(RequestLogFilter.class)) {
            filter.doFilter(request("POST", "/api/v1/auth/login"), new MockHttpServletResponse(), respondingWith(502, new ArrayList<>()));

            assertThat(logs.events()).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
        }
    }

    @Test
    void clientErrorsStayAtInfo() throws Exception {
        try (LogCapture logs = LogCapture.of(RequestLogFilter.class)) {
            filter.doFilter(request("GET", "/api/v1/pokemon/x"), new MockHttpServletResponse(), respondingWith(404, new ArrayList<>()));

            assertThat(logs.events()).extracting(ILoggingEvent::getLevel).containsExactly(Level.INFO);
        }
    }

    @Test
    void theHealthProbeIsLoggedAtDebugSoDockerPollingDoesNotFloodTheLogs() throws Exception {
        try (LogCapture logs = LogCapture.of(RequestLogFilter.class).at(Level.DEBUG)) {
            filter.doFilter(request("GET", "/actuator/health"), new MockHttpServletResponse(), respondingWith(200, new ArrayList<>()));

            assertThat(logs.events()).extracting(ILoggingEvent::getLevel).containsExactly(Level.DEBUG);
        }
    }
}
