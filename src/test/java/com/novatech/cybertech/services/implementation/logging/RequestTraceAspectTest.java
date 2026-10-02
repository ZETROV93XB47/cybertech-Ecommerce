package com.novatech.cybertech.services.implementation.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.novatech.cybertech.annotation.NotTraced;
import com.novatech.cybertech.api.error.enumpackage.ErrorCodeType;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.filter.LoggingFilter;
import com.novatech.cybertech.logger.RequestTraceAspect;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RequestTraceAspect}, driven through a real AspectJ proxy (no Spring
 * context). Lives under {@code services.implementation} on purpose: the aspect only advises beans
 * of the business packages, so the probe targets below must sit inside one.
 */
class RequestTraceAspectTest {

    private static final String KEYCLOAK_ID = "550e8400-e29b-41d4-a716-446655440000";

    private final RequestTraceAspect aspect = new RequestTraceAspect();
    private Logger traceLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
        traceLogger = (Logger) LoggerFactory.getLogger(RequestTraceAspect.class);
        originalLevel = traceLogger.getLevel();
        traceLogger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        traceLogger.addAppender(appender);
        MDC.clear();
    }

    @AfterEach
    void detachAppender() {
        traceLogger.detachAppender(appender);
        traceLogger.setLevel(originalLevel);
        MDC.clear();
    }

    // ---- probe beans -------------------------------------------------------------------

    static class InnerProbe {
        String reserve(final UUID productUuid, final String keycloakId) {
            return "ok";
        }

        public String reservePublic(final UUID productUuid, final String keycloakId) {
            return "ok";
        }

        public void fail() {
            throw new NotEnoughStockException("Not enough stock for product X");
        }

        public String currentRequestId() {
            return MDC.get(LoggingFilter.REQUEST_ID);
        }

        public void touch() {
            // no-op
        }

        public String nothing() {
            return null;
        }
    }

    static class OuterProbe {
        private final InnerProbe inner;

        OuterProbe(final InnerProbe inner) {
            this.inner = inner;
        }

        public String addToCart(final CartCreateRequestDto dto, final UUID productUuid) {
            return inner.reservePublic(productUuid, KEYCLOAK_ID);
        }

        public void failThroughInner() {
            inner.fail();
        }
    }

    @NotTraced
    static class SilentProbe {
        public String ping() {
            return "pong";
        }
    }

    private <T> T proxy(final T target) {
        final AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ---- tests ------------------------------------------------------------------------

    @Nested
    @DisplayName("call tree")
    class CallTree {

        @Test
        @DisplayName("nested calls are logged as an indented ▶ / ◀ tree with durations")
        void nestedCallsAreIndented() {
            final InnerProbe inner = proxy(new InnerProbe());
            final OuterProbe outer = proxy(new OuterProbe(inner));
            final UUID productUuid = UUID.randomUUID();

            outer.addToCart(new CartCreateRequestDto(), productUuid);

            assertThat(messages()).hasSize(4);
            assertThat(messages().get(0)).isEqualTo("▶ OuterProbe.addToCart(CartCreateRequestDto, " + productUuid + ")");
            assertThat(messages().get(1)).isEqualTo("  ▶ InnerProbe.reservePublic(" + productUuid + ", 550e84***)");
            assertThat(messages().get(2)).startsWith("  ◀ InnerProbe.reservePublic = String[2] (").endsWith(" ms)");
            assertThat(messages().get(3)).startsWith("◀ OuterProbe.addToCart = String[2] (");
        }

        @Test
        @DisplayName("DTO content is never printed — only its type")
        void dtoContentIsNotPrinted() {
            final OuterProbe outer = proxy(new OuterProbe(new InnerProbe()));

            outer.addToCart(CartCreateRequestDto.builder().cartItemAddRequestDtos(List.of()).build(), UUID.randomUUID());

            assertThat(messages().getFirst()).contains("CartCreateRequestDto").doesNotContain("cartItemAddRequestDtos");
        }

        @Test
        @DisplayName("a void method exits with 'void', a null return with 'null'")
        void voidAndNullResultsAreDistinguished() {
            final InnerProbe inner = proxy(new InnerProbe());

            inner.touch();
            inner.nothing();

            assertThat(messages().get(1)).startsWith("◀ InnerProbe.touch = void (");
            assertThat(messages().get(3)).startsWith("◀ InnerProbe.nothing = null (");
        }

        @Test
        @DisplayName("a call reaching an already-proxied target is traced once, by the inner proxy")
        void doubleProxyIsTracedOnce() {
            final InnerProbe twice = proxy(proxy(new InnerProbe()));

            twice.touch();

            assertThat(messages()).hasSize(2);
            assertThat(messages().getFirst()).isEqualTo("▶ InnerProbe.touch()");
        }

        @Test
        @DisplayName("@NotTraced beans are left out of the trace")
        void notTracedBeanIsSilent() {
            final SilentProbe silent = proxy(new SilentProbe());

            assertThat(silent.ping()).isEqualTo("pong");
            assertThat(messages()).isEmpty();
        }

        @Test
        @DisplayName("non-public methods are not traced")
        void nonPublicMethodIsSilent() {
            final InnerProbe inner = proxy(new InnerProbe());

            inner.reserve(UUID.randomUUID(), KEYCLOAK_ID);

            assertThat(messages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("failures")
    class Failures {

        @Test
        @DisplayName("the throwing frame reports the message at WARN, outer frames only note the propagation")
        void failureReportedOnceThenPropagated() {
            final OuterProbe outer = proxy(new OuterProbe(proxy(new InnerProbe())));

            assertThatThrownBy(outer::failThroughInner).isInstanceOf(NotEnoughStockException.class);

            final ILoggingEvent origin = appender.list.get(2);
            assertThat(origin.getLevel()).isEqualTo(Level.WARN);
            assertThat(origin.getFormattedMessage())
                    .startsWith("  ✖ InnerProbe.fail threw NotEnoughStockException: Not enough stock for product X (");

            final ILoggingEvent propagated = appender.list.get(3);
            assertThat(propagated.getLevel()).isEqualTo(Level.INFO);
            assertThat(propagated.getFormattedMessage())
                    .startsWith("✖ OuterProbe.failThroughInner ← NotEnoughStockException (")
                    .doesNotContain("Not enough stock");
        }

        @Test
        @DisplayName("the same exception thrown again in a later request is reported with its message again")
        void reportingStateIsResetBetweenRequests() {
            final InnerProbe inner = proxy(new InnerProbe());

            assertThatThrownBy(inner::fail).isInstanceOf(NotEnoughStockException.class);
            assertThatThrownBy(inner::fail).isInstanceOf(NotEnoughStockException.class);

            assertThat(appender.list).filteredOn(e -> e.getLevel() == Level.WARN).hasSize(2);
        }
    }

    @Nested
    @DisplayName("request id")
    class RequestId {

        @Test
        @DisplayName("work started outside any request gets a bg- request id, removed afterwards")
        void backgroundWorkGetsItsOwnRequestId() {
            final InnerProbe inner = proxy(new InnerProbe());

            final String seen = inner.currentRequestId();

            assertThat(seen).startsWith("bg-").hasSize(11);
            assertThat(MDC.get(LoggingFilter.REQUEST_ID)).isNull();
        }

        @Test
        @DisplayName("an existing request id (HTTP request, @Async copy) is kept untouched")
        void existingRequestIdIsKept() {
            MDC.put(LoggingFilter.REQUEST_ID, "abc12345");
            final InnerProbe inner = proxy(new InnerProbe());

            final AtomicReference<String> seen = new AtomicReference<>(inner.currentRequestId());

            assertThat(seen.get()).isEqualTo("abc12345");
            assertThat(MDC.get(LoggingFilter.REQUEST_ID)).isEqualTo("abc12345");
        }
    }

    @Nested
    @DisplayName("logHandledError")
    class HandledError {

        @Test
        @DisplayName("4xx verdict is logged at WARN with status, error type, exception and message")
        void clientErrorIsWarn() {
            final JoinPoint joinPoint = mock(JoinPoint.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{new NotEnoughStockException("Not enough stock")});
            final ResponseEntity<ErrorResponseDto> response = ResponseEntity.status(409)
                    .body(new ErrorResponseDto("Not enough stock", 409, ErrorCodeType.FUNCTIONAL));

            aspect.logHandledError(joinPoint, response);

            assertThat(appender.list).singleElement().satisfies(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.WARN);
                assertThat(e.getFormattedMessage()).isEqualTo("✖ 409 FUNCTIONAL — NotEnoughStockException: Not enough stock");
            });
        }

        @Test
        @DisplayName("5xx verdict is logged at ERROR")
        void serverErrorIsError() {
            final JoinPoint joinPoint = mock(JoinPoint.class);
            when(joinPoint.getArgs()).thenReturn(new Object[]{new IllegalStateException("boom")});
            final ResponseEntity<ErrorResponseDto> response = ResponseEntity.status(500)
                    .body(new ErrorResponseDto("Internal error", 500, ErrorCodeType.TECHNICAL));

            aspect.logHandledError(joinPoint, response);

            assertThat(appender.list).singleElement().extracting(ILoggingEvent::getLevel).isEqualTo(Level.ERROR);
        }
    }
}
