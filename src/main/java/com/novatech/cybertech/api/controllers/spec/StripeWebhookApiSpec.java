package com.novatech.cybertech.api.controllers.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;

public interface StripeWebhookApiSpec {

    @Operation(
            summary = "Stripe Webhook endpoint",
            description = "Receives Stripe webhook events for payments and refunds"
    )
    @ApiResponse(
            responseCode = "200",
            description = "Webhook received successfully"
    )
    ResponseEntity<Void> handleStripeEvent(String payload, String stripeSignature);
}