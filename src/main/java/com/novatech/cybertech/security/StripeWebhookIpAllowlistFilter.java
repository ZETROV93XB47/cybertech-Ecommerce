package com.novatech.cybertech.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Defence-in-depth IP allowlist for Stripe webhook deliveries.
 *
 * <p>Stripe-Signature HMAC verification (in {@link com.novatech.cybertech.api.controllers.implementation.StripeWebhookController})
 * already authenticates the payload, but a misconfigured (or rotated) webhook secret would be
 * the single point of failure if an attacker discovered the path. This filter adds a second
 * layer: the request's source IP MUST appear in Stripe's published webhook source ranges.
 *
 * <p>Source ranges are hardcoded as a snapshot (see the {@code STRIPE_WEBHOOK_IPS} constant).
 * Stripe publishes the canonical list at
 * <a href="https://stripe.com/files/ips/ips_webhooks.txt">https://stripe.com/files/ips/ips_webhooks.txt</a>;
 * the snapshot here MUST be reviewed during ops onboarding and refreshed on each Stripe
 * IP-rotation announcement (the link is also referenced in ProductionReadyLeftToDo).
 *
 * <p>The filter is gated by {@code cybertech.security.stripe-ip-allowlist.enabled} so it stays
 * off in dev/test (where webhooks are delivered from Stripe CLI / Mailpit / loopback). Production
 * flips it on via {@code application-prod.properties}.
 *
 * <p>Forwarded requests: when the app sits behind nginx-ingress
 * ({@code server.forward-headers-strategy=framework}) Spring rewrites
 * {@code request.getRemoteAddr()} to honour {@code X-Forwarded-For}, so we read the remote
 * address directly here and rely on the framework to surface the real client IP.
 */
@Slf4j
@Component
public class StripeWebhookIpAllowlistFilter extends OncePerRequestFilter {

    private static final String STRIPE_WEBHOOK_PATH_PREFIX = "/api/v1/webhooks/stripe";

    /**
     * Snapshot of <a href="https://stripe.com/files/ips/ips_webhooks.txt">Stripe's webhook
     * source IPs</a>. Refresh on each Stripe IP-rotation advisory.
     */
    private static final Set<String> STRIPE_WEBHOOK_IPS = Set.of(
            "3.18.12.63",
            "3.130.192.231",
            "13.235.14.237",
            "13.235.122.149",
            "18.211.135.69",
            "35.154.171.200",
            "52.15.183.38",
            "54.88.130.119",
            "54.88.130.237",
            "54.187.174.169",
            "54.187.205.235",
            "54.187.216.72"
    );

    @Value("${cybertech.security.stripe-ip-allowlist.enabled:false}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        final String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(STRIPE_WEBHOOK_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && STRIPE_WEBHOOK_IPS.contains(remoteAddr)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Stripe webhook delivery from non-allowlisted IP {} — rejecting with 403", remoteAddr);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"forbidden\",\"reason\":\"untrusted_source\"}");
    }
}
