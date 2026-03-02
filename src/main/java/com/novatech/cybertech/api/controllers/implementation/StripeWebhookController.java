package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.StripeWebhookApiSpec;
import com.novatech.cybertech.services.core.PaymentWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.novatech.cybertech.constants.CyberTechAppConstants.STRIPE_WEBHOOKS_BASE_PATH;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(STRIPE_WEBHOOKS_BASE_PATH)
@Tag(name = "StripeWebhookController", description = "Stripe Webhook API")
public class StripeWebhookController implements StripeWebhookApiSpec {

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private final PaymentWebhookService paymentWebhookService;

    @Override
    @PostMapping
    public ResponseEntity<Void> handleStripeEvent(@RequestBody String payload, @RequestHeader(STRIPE_SIGNATURE_HEADER) String stripeSignature) {

        final Event event;

        try {
            event = Webhook.constructEvent(
                    payload,
                    stripeSignature,
                    webhookSecret
            );
        }
        catch (SignatureVerificationException e) {
            log.error("Invalid Stripe signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        log.info("Stripe event received: {}", event.getType());

        paymentWebhookService.handleEvent(event, payload);

        return ResponseEntity.ok().build();
    }
}