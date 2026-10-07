package com.ballastlane.assessment.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id that appears on each of its log lines and in the response, and writes one access line.
 * It runs before Spring Security so rejected requests (401/403) are correlated and logged too. Only method, path,
 * status and duration are logged: headers, bodies and query strings can carry tokens and search text.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);
    // A strict whitelist: a client-supplied id is written into logs, so newlines and odd characters must never get in.
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final String HEALTH_PATH = "/actuator/health";
    private static final long NANOS_PER_MILLI = 1_000_000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        long start = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            // A chain that threw never wrote its status: the container is about to answer 500.
            int status = completed ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            logAccess(request, status, (System.nanoTime() - start) / NANOS_PER_MILLI);
            MDC.clear();
        }
    }

    private static String requestId(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER);
        return incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }

    private static void logAccess(HttpServletRequest request, int status, long millis) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            log.warn("{} {} {} {}ms", method, path, status, millis);
        } else if (path.startsWith(HEALTH_PATH)) {
            // Docker polls this every few seconds; at INFO it would drown the real traffic.
            log.debug("{} {} {} {}ms", method, path, status, millis);
        } else {
            log.info("{} {} {} {}ms", method, path, status, millis);
        }
    }
}
