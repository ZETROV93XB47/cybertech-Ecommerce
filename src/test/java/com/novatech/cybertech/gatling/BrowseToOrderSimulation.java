package com.novatech.cybertech.gatling;

import io.gatling.javaapi.core.CoreDsl;
import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * BrowseToOrderSimulation
 *
 * <p>Happy-path RPS-driven load test for the Cybertech catalog -> cart -> order flow.
 * Scenario:
 *   1. GET /api/v1/services/product/search        (list products, paginated)
 *   2. GET /api/v1/services/product/get/{uuid}    (product detail)
 *   3. POST /api/v1/services/cart/add             (add to cart)
 *   4. POST /api/v1/services/management/order/place (place order)
 *
 * <p>Injected at three increasing RPS plateaus (50, 200, 500) with 5-min ramps, to map
 * latency and error-rate growth as we scale up arrival rate.
 *
 * <p>The JWT and base URL are injected via JVM system properties so the same simulation
 * file is reused across local / staging / pre-prod runs.
 *
 * <p>SLO assertions (starting points; tune once we have steady-state numbers):
 *   - global p95 less than 1000 ms
 *   - global error rate less than 1 percent
 *
 * <p>If breached: see src/test/java/com/novatech/cybertech/gatling/README.md.
 */
public class BrowseToOrderSimulation extends Simulation {

    private static final String BASE_URL =
        System.getProperty("gatling.baseUrl", "http://api.cybertech.local");

    /**
     * Bearer token obtained out-of-band from Keycloak. Defaults to a placeholder so the
     * simulation compiles in CI without secrets; before a real run, override with
     * -Dauth.token=eyJhbGciOi...
     */
    private static final String AUTH_TOKEN =
        System.getProperty("auth.token", "PLACEHOLDER_REPLACE_BEFORE_RUN");

    private final HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .header("X-API-VERSION", "1.0")
        .authorizationHeader("Bearer " + AUTH_TOKEN)
        .userAgentHeader("CybertechGatling/1.0 (BrowseToOrderSimulation)");

    private final FeederBuilder<String> productFeeder = csv("gatling/data/products.csv").random();
    private final FeederBuilder<String> userFeeder = csv("gatling/data/users.csv").random();

    private final ScenarioBuilder browseToOrder = scenario("Browse Cart Order happy path")
        .feed(userFeeder)
        .feed(productFeeder)
        .exec(
            http("List products")
                .get("/api/v1/services/product/search?page=0&size=20")
                .check(status().in(200, 304))
        )
        .pause(Duration.ofSeconds(1), Duration.ofSeconds(3))
        .exec(
            http("Product detail")
                .get("/api/v1/services/product/get/#{productUuid}")
                .check(status().in(200, 404))
        )
        .pause(Duration.ofMillis(500), Duration.ofSeconds(2))
        .exec(
            http("Add to cart")
                .post("/api/v1/services/cart/add")
                .body(CoreDsl.StringBody(
                    "{\"cartItemAddRequestDtos\":[{\"productUuid\":\"#{productUuid}\","
                        + "\"quantity\":1}]}"
                ))
                .check(status().in(200, 201, 409))
        )
        .pause(Duration.ofSeconds(1), Duration.ofSeconds(2))
        .exec(
            http("Place order")
                .post("/api/v1/services/management/order/place")
                .body(CoreDsl.StringBody(
                    "{"
                        + "\"paymentType\":\"VISA\","
                        + "\"shippingType\":\"STANDARD\","
                        + "\"shippingProvider\":\"DHL\","
                        + "\"shippingStreet\":\"21 Rue du Test\","
                        + "\"shippingCity\":\"Paris\","
                        + "\"shippingZipCode\":\"75001\","
                        + "\"shippingCountry\":\"France\","
                        + "\"discountType\":\"NO_DISCOUNT\""
                        + "}"
                ))
                .check(status().in(200, 201, 400, 402, 409))
        );

    {
        setUp(
            browseToOrder.injectOpen(
                // Plateau 1 - 50 RPS, 5 min ramp + 5 min steady
                rampUsersPerSec(1).to(50).during(Duration.ofMinutes(5)),
                constantUsersPerSec(50).during(Duration.ofMinutes(5)),
                // Plateau 2 - 200 RPS, 5 min ramp + 5 min steady
                rampUsersPerSec(50).to(200).during(Duration.ofMinutes(5)),
                constantUsersPerSec(200).during(Duration.ofMinutes(5)),
                // Plateau 3 - 500 RPS, 5 min ramp + 5 min steady
                rampUsersPerSec(200).to(500).during(Duration.ofMinutes(5)),
                constantUsersPerSec(500).during(Duration.ofMinutes(5))
            )
        )
        .protocols(httpProtocol)
        .assertions(
            global().responseTime().percentile(95).lt(1000),
            global().failedRequests().percent().lt(1.0),
            details("Add to cart").failedRequests().percent().lt(2.0),
            details("Place order").failedRequests().percent().lt(2.0)
        );
    }
}
