package com.novatech.cybertech.api.controllers.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * OpenAPI / SpringDoc specification for the Stripe webhook endpoint.
 *
 * <p>Documents the response-code calibration described in the controller Javadoc:
 * <ul>
 *   <li><b>200</b> — accepted (or non-retriable downstream failure).</li>
 *   <li><b>400</b> — signature verification failed or payload malformed.</li>
 * </ul>
 */
@Tag(name = "Stripe Webhook", description = "Stripe webhook ingestion endpoint (publicly reachable, secured via HMAC-SHA256 Stripe-Signature header)")
public interface StripeWebhookApiSpec {

    /**
     * Stripe webhook entry point. See implementation for the full retry-aware response contract.
     *
     * @param payload         the raw request body (preserved for HMAC verification)
     * @param stripeSignature the {@code Stripe-Signature} header
     * @return a 200/400 response per the documented contract
     */
    @Operation(
            summary = "Stripe Webhook endpoint",
            description = "Receives Stripe webhook events for payments and refunds. " +
                    "Returns 200 on accepted/non-retriable; 400 on invalid signature or malformed payload."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook accepted (or non-retriable downstream failure ACKed to stop Stripe retries)"),
            @ApiResponse(responseCode = "400", description = "Invalid Stripe signature or malformed event payload")
    })
    ResponseEntity<Void> handleStripeEvent(String payload, String stripeSignature);
}
