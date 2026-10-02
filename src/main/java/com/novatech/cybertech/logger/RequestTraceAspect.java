package com.novatech.cybertech.logger;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.filter.LoggingFilter;
import com.novatech.cybertech.utils.LogSafetyUtils;
import com.novatech.cybertech.utils.LogTraceUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Writes the call tree of every request — controller, then each service / delegate / validator /
 * listener / client it goes through — so the log of one request reads top to bottom:
 *
 * <pre>
 * → POST /api/v1/services/cart/add
 * ▶ CartManagementController.addToCart(CartCreateRequestDto, Jwt)
 *   ▶ CartServiceImp.addItemsToCart(CartCreateRequestDto, 01a0fc***)
 *     ▶ CartWriteTransactionalDelegateImp.addItemsWithinTransaction(CartCreateRequestDto, 01a0fc***)
 *     ◀ CartWriteTransactionalDelegateImp.addItemsWithinTransaction = CartResponseDto (23 ms)
 *   ◀ CartServiceImp.addItemsToCart = CartResponseDto (31 ms)
 * ◀ CartManagementController.addToCart = HTTP 201 CartResponseDto (33 ms)
 * ← 201 POST /api/v1/services/cart/add (35 ms) user=01a0fc***
 * </pre>
 *
 * <p>A failure is reported once, with its message, at the frame that threw it ({@code WARN});
 * the frames it then crosses only note that it went through. The exception handler's verdict
 * (status + error code) is logged by {@link #logHandledError}. Arguments are summarised by
 * {@link LogTraceUtils} — ids are printed, payload content never is.
 *
 * <p>Runs as (almost) the outermost advice ({@link #TRACE_ORDER}) so a duration includes the
 * transaction commit and a commit failure is attributed to the right call. {@code @Async} methods
 * are traced on the worker thread (the async interceptor always runs first), where the MDC copied
 * by {@code AppConfig}'s task decorator keeps the originating request id. Work that starts outside
 * any request (scheduled jobs, Kafka / Redis listeners) gets its own {@code bg-} request id.
 *
 * <p>Plumbing that is called several times per request opts out with
 * {@link com.novatech.cybertech.annotation.NotTraced}. The whole trace can be silenced with
 * {@code logging.level.com.novatech.cybertech.logger.RequestTraceAspect=WARN} (failures are still
 * reported).
 */
@Slf4j
@Aspect
@Component
@Order(RequestTraceAspect.TRACE_ORDER)
public class RequestTraceAspect {

    /**
     * Just inside Spring's {@code ExposeInvocationInterceptor} ({@code HIGHEST_PRECEDENCE + 1}):
     * {@link #logHandledError}'s {@code @AfterReturning} needs the invocation it exposes — at
     * {@code HIGHEST_PRECEDENCE} every advised {@code @ExceptionHandler} call fails with
     * "No MethodInvocation found". Still outside {@code @Transactional} and Resilience4j.
     */
    static final int TRACE_ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    private static final String BACKGROUND_REQUEST_ID_PREFIX = "bg-";
    private static final int BACKGROUND_REQUEST_ID_LENGTH = 8;
    private static final int SERVER_ERROR_THRESHOLD = 500;
    private static final String VOID = "void";

    /** Current nesting depth on this thread — drives the indentation of the call tree. */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    /** Last exception already reported with its message, so outer frames do not repeat it. */
    private static final ThreadLocal<Throwable> LAST_REPORTED_FAILURE = new ThreadLocal<>();

    @Pointcut("within(com.novatech.cybertech.api.controllers.implementation..*)")
    void controllerLayer() {
    }

    @Pointcut("within(com.novatech.cybertech.services.implementation..*)"
            + " || within(com.novatech.cybertech.validator.implementation..*)"
            + " || within(com.novatech.cybertech.listener..*)"
            + " || within(com.novatech.cybertech.events.listener..*)"
            + " || within(com.novatech.cybertech.dispatcher..*)"
            + " || within(com.novatech.cybertech.clients..*)"
            + " || within(com.novatech.cybertech.batch..*)")
    void businessLayer() {
    }

    @Pointcut("execution(public * *(..))"
            + " && !execution(String toString())"
            + " && !execution(int hashCode())"
            + " && !execution(boolean equals(Object))")
    void publicBusinessMethod() {
    }

    @Pointcut("@within(com.novatech.cybertech.annotation.NotTraced)"
            + " || @annotation(com.novatech.cybertech.annotation.NotTraced)")
    void optedOut() {
    }

    @Pointcut("within(com.novatech.cybertech.api.error..*)"
            + " && @annotation(org.springframework.web.bind.annotation.ExceptionHandler)")
    void exceptionHandler() {
    }

    @Around("(controllerLayer() || businessLayer()) && publicBusinessMethod() && !optedOut()")
    public Object trace(final ProceedingJoinPoint joinPoint) throws Throwable {
        if (AopUtils.isAopProxy(joinPoint.getTarget())) {
            // Bean re-exposed under a second name (e.g. AppConfig#orderValidatorChain returns the
            // already-proxied ActiveUserValidator) and therefore proxied twice: the inner proxy
            // traces the call, tracing it here as well would only print every line twice.
            return joinPoint.proceed();
        }
        final int depth = DEPTH.get();
        final boolean startsBackgroundTrace = depth == 0 && MDC.get(LoggingFilter.REQUEST_ID) == null;
        if (startsBackgroundTrace) {
            MDC.put(LoggingFilter.REQUEST_ID, BACKGROUND_REQUEST_ID_PREFIX
                    + UUID.randomUUID().toString().substring(0, BACKGROUND_REQUEST_ID_LENGTH));
        }
        if (MDC.get(LoggingFilter.USER) == null) {
            rememberAuthenticatedUser();
        }

        final String call = joinPoint.getTarget().getClass().getSimpleName() + "." + joinPoint.getSignature().getName();
        final String indent = LogTraceUtils.indent(depth);
        if (log.isInfoEnabled()) {
            log.info("{}▶ {}{}", indent, call, LogTraceUtils.summarizeArgs(joinPoint.getArgs()));
        }

        final long start = System.nanoTime();
        DEPTH.set(depth + 1);
        try {
            final Object result = joinPoint.proceed();
            log.info("{}◀ {} = {} ({} ms)", indent, call, summarizeResult(joinPoint, result), elapsedMs(start));
            return result;
        } catch (Throwable failure) {
            reportFailure(indent, call, failure, elapsedMs(start));
            throw failure;
        } finally {
            DEPTH.set(depth);
            if (depth == 0) {
                DEPTH.remove();
                LAST_REPORTED_FAILURE.remove();
                if (startsBackgroundTrace) {
                    MDC.remove(LoggingFilter.REQUEST_ID);
                    MDC.remove(LoggingFilter.USER);
                }
            }
        }
    }

    /**
     * One line per handled exception with the HTTP verdict, e.g.
     * {@code ✖ 409 FUNCTIONAL — NotEnoughStockException: Not enough stock for product ...}. Covers
     * failures raised before any controller method ran too (bean validation, malformed JSON, type
     * mismatch). 5xx are logged at ERROR — the catch-all handler already prints their stack trace.
     */
    @AfterReturning(pointcut = "exceptionHandler()", returning = "response")
    public void logHandledError(final JoinPoint joinPoint, final Object response) {
        // Validation / binding failures never reach a controller method, so the caller may not be
        // in the MDC yet — the security context is still populated here.
        if (MDC.get(LoggingFilter.USER) == null) {
            rememberAuthenticatedUser();
        }
        final Object[] args = joinPoint.getArgs();
        final String exceptionName = args.length > 0 && args[0] instanceof Throwable t ? t.getClass().getSimpleName() : "?";
        if (!(response instanceof ResponseEntity<?> entity) || !(entity.getBody() instanceof ErrorResponseDto error)) {
            log.warn("✖ handled {}", exceptionName);
            return;
        }
        if (error.getHttpStatusCode() >= SERVER_ERROR_THRESHOLD) {
            log.error("✖ {} {} — {}: {}", error.getHttpStatusCode(), error.getErrorCodeType(), exceptionName, error.getMessage());
        } else {
            log.warn("✖ {} {} — {}: {}", error.getHttpStatusCode(), error.getErrorCodeType(), exceptionName, error.getMessage());
        }
    }

    private static void reportFailure(final String indent, final String call, final Throwable failure, final long elapsedMs) {
        if (LAST_REPORTED_FAILURE.get() == failure) {
            log.info("{}✖ {} ← {} ({} ms)", indent, call, failure.getClass().getSimpleName(), elapsedMs);
            return;
        }
        LAST_REPORTED_FAILURE.set(failure);
        log.warn("{}✖ {} threw {}: {} ({} ms)", indent, call, failure.getClass().getSimpleName(), failure.getMessage(), elapsedMs);
    }

    private static String summarizeResult(final ProceedingJoinPoint joinPoint, final Object result) {
        if (joinPoint.getSignature() instanceof MethodSignature signature && signature.getReturnType() == void.class) {
            return VOID;
        }
        return LogTraceUtils.summarize(result);
    }

    private static void rememberAuthenticatedUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            MDC.put(LoggingFilter.USER, LogSafetyUtils.maskUuid(authentication.getName()));
        }
    }

    private static long elapsedMs(final long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
