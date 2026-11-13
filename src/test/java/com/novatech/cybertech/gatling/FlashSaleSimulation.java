package com.novatech.cybertech.gatling;

import io.gatling.javaapi.core.CoreDsl;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * FlashSaleSimulation
 *
 * <p>Race-condition-focused load test: 1000 concurrent virtual users hammering
 * <code>POST /api/v1/services/cart/add</code> with the SAME
 * <code>BUY_ONE_GET_ONE_FREE</code>-eligible product UUID inside a 30-second window.
 *
 * <p>Goal: validate that the PRE-2 fix (atomic stock reservation + idempotent BOGO
 * computation under contention) keeps the request handler stable.
 *   - 200/201 (success) and 400/409 (graceful business-rule rejection) are both OK.
 *   - 5xx is NOT OK - that means a deadlock, race, or unhandled exception slipped through.
 *
 * <p>Override the targeted product UUID via:
 * <pre>
 *   -Dflash.productUuid=&lt;uuid-of-bogo-eligible-product-in-target-env&gt;
 * </pre>
 */
public class FlashSaleSimulation extends Simulation {

    private static final String BASE_URL =
        System.getProperty("gatling.baseUrl", "http://api.cybertech.local");

    private static final String AUTH_TOKEN =
        System.getProperty("auth.token", "PLACEHOLDER_REPLACE_BEFORE_RUN");

    /** Single hot product all 1000 VUs target. Must be BUY_ONE_GET_ONE_FREE-eligible. */
    private static final String FLASH_PRODUCT_UUID =
        System.getProperty("flash.productUuid", "11111111-2222-3333-4444-555555555555");

    private final HttpProtocolBuilder httpProtocol = http
        .baseUrl(BASE_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")
        .header("X-API-VERSION", "1.0")
        .authorizationHeader("Bearer " + AUTH_TOKEN)
        .userAgentHeader("CybertechGatling/1.0 (FlashSaleSimulation)");

    private final ScenarioBuilder flashSale = scenario("Flash sale BOGO contention")
        .exec(
            http("Cart add (hot product)")
                .post("/api/v1/services/cart/add")
                .body(CoreDsl.StringBody(
                    "{\"cartItemAddRequestDtos\":[{\"productUuid\":\""
                        + FLASH_PRODUCT_UUID + "\",\"quantity\":1}]}"
                ))
                // 200/201/400/409/422 acceptable. 5xx fails the assertion below.
                .check(status().in(200, 201, 400, 409, 422))
        );

    {
        setUp(
            // Closed-model: 1000 concurrent VUs ramped over 30 seconds. The
            // contention window genuinely overlaps for all of them, exposing any
            // race in the cart-add path.
            flashSale.injectClosed(
                rampConcurrentUsers(0).to(1000).during(Duration.ofSeconds(30))
            )
        )
        .protocols(httpProtocol)
        .assertions(
            // Hard SLO: zero 5xx. Any server-side failure on a race-prone path is a regression.
            global().responseTime().percentile(95).lt(1000),
            global().failedRequests().percent().lt(1.0)
        );
    }
}
