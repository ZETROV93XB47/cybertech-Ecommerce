package com.novatech.cybertech.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LoggingFilter}: request id resolution / propagation, MDC lifecycle and the
 * {@code →} / {@code ←} request lines.
 */
class LoggingFilterTest {

    private static final String CART_ADD_PATH = "/api/v1/services/cart/add";

    private final LoggingFilter filter = new LoggingFilter();
    private Logger filterLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        filterLogger = (Logger) LoggerFactory.getLogger(LoggingFilter.class);
        originalLevel = filterLogger.getLevel();
        filterLogger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(appender);
        filterLogger.setLevel(originalLevel);
        MDC.clear();
    }

    @Test
    @DisplayName("generates a short request id, exposes it in MDC during the chain and echoes it in the response")
    void generatesAndEchoesRequestId() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", CART_ADD_PATH);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInChain.set(MDC.get(LoggingFilter.REQUEST_ID)));

        assertThat(seenInChain.get()).hasSize(8);
        assertThat(response.getHeader(LoggingFilter.REQUEST_ID_HEADER)).isEqualTo(seenInChain.get());
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("reuses a well-formed incoming X-Request-Id")
    void reusesIncomingRequestId() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/services/cart/get");
        request.addHeader(LoggingFilter.REQUEST_ID_HEADER, "front-1234");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(LoggingFilter.REQUEST_ID_HEADER)).isEqualTo("front-1234");
    }

    @Test
    @DisplayName("ignores an incoming X-Request-Id carrying unsafe characters (log forging)")
    void rejectsUnsafeIncomingRequestId() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/services/cart/get");
        request.addHeader(LoggingFilter.REQUEST_ID_HEADER, "abc\nFAKE LOG LINE");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(LoggingFilter.REQUEST_ID_HEADER)).hasSize(8).doesNotContain("\n");
    }

    @Test
    @DisplayName("logs a → line on entry and a ← line with status, duration and caller on exit")
    void logsInAndOutLines() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", CART_ADD_PATH);
        request.setQueryString("dryRun=true");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final FilterChain chain = (req, res) -> {
            MDC.put(LoggingFilter.USER, "550e84***");
            ((MockHttpServletResponse) res).setStatus(201);
        };

        filter.doFilter(request, response, chain);

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("→ POST " + CART_ADD_PATH + "?dryRun=true");
        assertThat(appender.list.get(1).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(1).getFormattedMessage())
                .startsWith("← 201 POST " + CART_ADD_PATH + " (")
                .endsWith(" ms) user=550e84***");
    }

    @Test
    @DisplayName("4xx / 5xx exit lines are WARN, anonymous caller is reported as such")
    void errorStatusIsWarn() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/services/cart/get");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(401));

        final ILoggingEvent exit = appender.list.get(1);
        assertThat(exit.getLevel()).isEqualTo(Level.WARN);
        assertThat(exit.getFormattedMessage()).startsWith("← 401 GET").endsWith("user=anonymous");
    }

    @Test
    @DisplayName("an exception escaping the chain is logged as FAILED, rethrown, and the MDC is still cleared")
    void unhandledExceptionIsLoggedAndRethrown() {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/services/cart/get");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(appender.list.get(1).getLevel()).isEqualTo(Level.ERROR);
        assertThat(appender.list.get(1).getFormattedMessage()).contains("FAILED GET").contains("IllegalStateException: boom");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("actuator / swagger requests get a request id but no in/out lines")
    void infraPathsAreSilent() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(appender.list).isEmpty();
        assertThat(response.getHeader(LoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
    }
}
