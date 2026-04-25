package com.novatech.cybertech.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * In-memory token-bucket rate limit (Bucket4j) for sensitive endpoints.
 *
 * <p>Buckets are keyed by {@code policyId + ":" + clientId}, where {@code clientId} is the JWT
 * subject when the request is authenticated and the remote address otherwise. The map is
 * unbounded across the lifetime of the JVM — acceptable for the current single-replica deployment;
 * a Redis-backed Bucket4j proxy is the next step when we scale out (see ProductionReadyLeftToDo).
 *
 * <p>Per-endpoint policies (see {@link Policy}):
 * <ul>
 *   <li>{@code POST /api/v1/services/user/register} — 5 req / 1 min / IP</li>
 *   <li>{@code POST /api/v1/services/order/place*} — 30 req / 1 min / JWT-sub (or IP fallback)</li>
 *   <li>{@code /api/v1/services/cart/**} — 60 req / 1 min / JWT-sub (or IP fallback)</li>
 * </ul>
 *
 * <p>The login policy was deliberately skipped: Cybertech delegates authentication to Keycloak
 * via the OAuth2 resource-server flow — the Spring backend has no Spring-managed login endpoint
 * to throttle. Brute-force protection on the login surface is owned by Keycloak's brute-force
 * detector (configured at the realm level).
 *
 * <p>On exhaustion the filter writes a {@code 429 Too Many Requests} with a {@code Retry-After}
 * header (seconds) computed from the bucket refill interval.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Exact path of the human-signup endpoint (NOT a prefix — the admin-only
     * {@code /register/auto/**} endpoints share the same prefix and would otherwise
     * trip the per-IP register quota during admin bulk loads / tests).
     */
    private static final String REGISTER_EXACT_PATH = "/api/v1/services/user/register";
    private static final String ORDER_PLACE_PATH_PREFIX = "/api/v1/services/management/order/place";
    private static final String CART_PATH_PREFIX = "/api/v1/services/cart";

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {

        final Policy policy = matchPolicy(request);

        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        final String clientId = resolveClientId(request);
        final String bucketKey = policy.id() + ":" + clientId;
        final Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> policy.bucketSupplier().get());

        final ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        final long retryAfterSeconds = Math.max(1L,
                Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());

        log.warn("Rate limit hit policy={} client={} retryAfter={}s", policy.id(), clientId, retryAfterSeconds);

        // 429 — HttpServletResponse exposes constants only up to SC_GATEWAY_TIMEOUT.
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"too_many_requests\",\"retryAfterSeconds\":" + retryAfterSeconds + "}");
    }

    private Policy matchPolicy(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        if (uri == null) {
            return null;
        }
        for (final Policy p : POLICIES) {
            final boolean pathMatches = p.exact() ? uri.equals(p.pathPrefix()) : uri.startsWith(p.pathPrefix());
            if (pathMatches && (p.method() == null || p.method().equalsIgnoreCase(request.getMethod()))) {
                return p;
            }
        }
        return null;
    }

    private String resolveClientId(final HttpServletRequest request) {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            final String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return "sub:" + sub;
            }
        }
        return "ip:" + Objects.requireNonNullElse(request.getRemoteAddr(), "unknown");
    }

    // --------------------------------------------------------------------------------
    // Policy table
    // --------------------------------------------------------------------------------

    private static Bucket newBucket(final long capacity, final Duration refillPeriod) {
        final Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, refillPeriod));
        return Bucket.builder().addLimit(limit).build();
    }

    private record Policy(String id, String pathPrefix, boolean exact, String method, Supplier<Bucket> bucketSupplier) {}

    private static final List<Policy> POLICIES = List.of(
            // Exact match — admin-only /register/auto/** lives under the same prefix and
            // must NOT count against the per-IP human-signup quota.
            new Policy(
                    "register",
                    REGISTER_EXACT_PATH,
                    true,
                    "POST",
                    () -> newBucket(5L, Duration.ofMinutes(1L))),
            new Policy(
                    "order.place",
                    ORDER_PLACE_PATH_PREFIX,
                    false,
                    "POST",
                    () -> newBucket(30L, Duration.ofMinutes(1L))),
            new Policy(
                    "cart",
                    CART_PATH_PREFIX,
                    false,
                    null,
                    () -> newBucket(60L, Duration.ofMinutes(1L)))
    );

    // Visible for testing / introspection.
    Map<String, Bucket> bucketsView() {
        return Map.copyOf(buckets);
    }
}
