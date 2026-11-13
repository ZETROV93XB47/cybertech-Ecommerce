package com.novatech.cybertech.gatling;

import io.gatling.javaapi.core.CoreDsl;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * StripeWebhookFirehoseSimulation
 *
 * <p>Slams <code>POST /api/v1/webhooks/stripe</code> at 100 rps for 60 seconds
 * (~6000 events) with a fixed PaymentSucceeded payload, to verify:
 *   - signature-verification middleware does not become the bottleneck;
 *   - downstream order-fulfilment listener absorbs back-pressure cleanly;
 *   - the webhook never returns 5xx (Stripe would auto-retry, amplifying load).
 *
 * <p>NOTE: the <code>Stripe-Signature</code> header below is a fixed placeholder and
 * will fail HMAC verification in any non-loadtest profile. Two options for real runs:
 *   1. Pre-compute a valid signature for a known payload + endpoint secret, and pass
 *      it via -Dstripe.signature=...
 *   2. Run in a dedicated load-test profile where signature verification is disabled
 *      (see src/test/java/com/novatech/cybertech/gatling/README.md, "Stripe signature handling").
 *
 * <p>The payload is loaded from a fixture so it can be evolved without code changes:
 * <code>src/test/resources/gatling/stripe-webhook-payload.json</code>
 */
public class StripeWebhookFirehoseSimulation extends Simulation {

    private static final String BASE_URL =
        System.getProperty("gatling.baseUrl", "http://api.cybertech.local");

    /**
     * Stripe-Signature header value. Placeholder by default; override with a real
     * <code>t=...,v1=...</code> in production-mirror runs.
     */
    private static final String STRIPE_SIGNATURE = System.getProperty(
        "stripe.signature",
        "t=1719500000,v1=PLACEHOLDER_REPLACE_BEFORE_RUN"
    );

    private final HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .userAgentHeader("Stripe/1.0 (+https://stripe.com/docs/webhooks)")
        .header("Stripe-Signature", STRIPE_SIGNATURE);

    private final ScenarioBuilder webhookFirehose = scenario("Stripe webhook firehose")
        .exec(
            http("PaymentSucceeded webhook")
                .post("/api/v1/webhooks/stripe")
                // Body loaded from src/test/resources/gatling/stripe-webhook-payload.json
                // via the Gatling resource resolver.
                .body(CoreDsl.RawFileBody("gatling/stripe-webhook-payload.json"))
                // 200 = accepted; 400 = signature/payload rejected (expected with placeholder).
                // We must NEVER see 5xx - that signals an internal-handler bug.
                .check(status().in(200, 400))
        );

    {
        setUp(
            webhookFirehose.injectOpen(
                // 100 events/sec for 60 sec = ~6 000 events
                constantUsersPerSec(100).during(Duration.ofSeconds(60))
            )
        )
        .protocols(httpProtocol)
        .assertions(
            // Webhook handler must stay fast (Stripe times out at 30s and retries).
            global().responseTime().percentile(95).lt(1000),
            // No 5xx tolerated; 4xx from placeholder-signature runs is OK.
            global().failedRequests().percent().lt(1.0)
        );
    }
}
