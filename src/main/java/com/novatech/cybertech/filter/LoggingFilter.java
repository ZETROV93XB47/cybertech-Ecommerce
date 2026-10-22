package com.novatech.cybertech.filter;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Opens and closes the log trace of every HTTP request.
 *
 * <ul>
 *   <li>Puts a short {@value #REQUEST_ID} in the MDC — every log line written while serving the
 *       request carries it, so {@code grep <requestId>} gives the full story of one request. An
 *       incoming {@value #REQUEST_ID_HEADER} header is reused when it looks sane (lets the
 *       frontend correlate its own logs), and the id is always echoed back in the response header
 *       so a failing Postman / browser call can be matched to its server-side trace.</li>
 *   <li>Logs one {@code →} line when the request comes in and one {@code ←} line with the final
 *       status and duration when it leaves. In between, {@code RequestTraceAspect} writes the
 *       controller → service call tree.</li>
 * </ul>
 *
 * <p>Runs first in the servlet chain (before Spring Security), so requests rejected with
 * 401 / 403 by the security filters are traced too.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    public static final String METHOD = "method";
    public static final String PATH = "path";
    /** Masked Keycloak subject of the caller, set by {@code RequestTraceAspect} once authenticated. */
    public static final String USER = "user";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final String ANONYMOUS = "anonymous";
    private static final int GENERATED_REQUEST_ID_LENGTH = 8;
    private static final Pattern SAFE_INCOMING_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int CLIENT_ERROR_THRESHOLD = 400;
    /** Infra / doc endpoints still get a request id, but no in/out lines (probes would flood the log). */
    private static final List<String> SILENT_PATH_PREFIXES = List.of("/actuator", "/swagger-ui", "/v3/api-docs", "/favicon.ico");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        final String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID, requestId);
        MDC.put(METHOD, request.getMethod());
        MDC.put(PATH, request.getRequestURI());
        response.setHeader(REQUEST_ID_HEADER, requestId);

        final boolean traced = isTraced(request);
        final long start = System.nanoTime();
        if (traced) {
            log.info("→ {} {}{}", request.getMethod(), request.getRequestURI(), formatQuery(request));
        }

        try {
            filterChain.doFilter(request, response);
            if (traced) {
                logCompletion(request, response.getStatus(), start);
            }
        } catch (IOException | ServletException | RuntimeException e) {
            // Only reached when nothing downstream (ErrorManagementController, security handlers)
            // turned the failure into a response — the container will answer 500.
            if (traced) {
                log.error("← FAILED {} {} ({} ms) user={} — {}: {}", request.getMethod(), request.getRequestURI(),
                        elapsedMs(start), currentUser(), e.getClass().getSimpleName(), e.getMessage());
            }
            throw e;
        } finally {
            MDC.clear(); // TRÈS IMPORTANT
        }
    }

    private static void logCompletion(final HttpServletRequest request, final int status, final long start) {
        if (status >= CLIENT_ERROR_THRESHOLD) {
            log.warn("← {} {} {} ({} ms) user={}", status, request.getMethod(), request.getRequestURI(), elapsedMs(start), currentUser());
        } else {
            log.info("← {} {} {} ({} ms) user={}", status, request.getMethod(), request.getRequestURI(), elapsedMs(start), currentUser());
        }
    }

    private static String resolveRequestId(final HttpServletRequest request) {
        final String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && SAFE_INCOMING_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().substring(0, GENERATED_REQUEST_ID_LENGTH);
    }

    private static boolean isTraced(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        return SILENT_PATH_PREFIXES.stream().noneMatch(uri::startsWith);
    }

    private static String formatQuery(final HttpServletRequest request) {
        final String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return "";
        }
        return "?" + (query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) + "…" : query);
    }

    private static String currentUser() {
        final String user = MDC.get(USER);
        return user == null ? ANONYMOUS : user;
    }

    private static long elapsedMs(final long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
