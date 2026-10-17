package com.novatech.cybertech.fixtures.dto;

import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.dto.request.stripe.PaymentIntentPayload;
import com.novatech.cybertech.dto.request.stripe.StripeWebhookEventDto;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.StripeEventType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tiny DTO factories for the payment/Stripe surface.
 */
public final class PaymentDtoFixtures {

    private PaymentDtoFixtures() {
    }

    public static PaymentAttemptResult aSuccessfulAttemptResult() {
        return new PaymentAttemptResult(PaymentAttemptStatus.SUCCESS, "pi_" + UUID.randomUUID());
    }

    public static PaymentAttemptResult aFailedAttemptResult() {
        return new PaymentAttemptResult(PaymentAttemptStatus.FAILED, "pi_" + UUID.randomUUID());
    }

    public static PaymentIntentPayload aValidPaymentIntentPayload() {
        PaymentIntentPayload payload = new PaymentIntentPayload();
        payload.setId("pi_" + UUID.randomUUID());
        payload.setObject("payment_intent");
        payload.setAmount(10000L);
        payload.setAmount_received(10000L);
        payload.setCurrency("eur");
        payload.setLatest_charge("ch_" + UUID.randomUUID());
        payload.setPayment_method("pm_card_visa");
        payload.setPayment_method_types(List.of("card"));
        payload.setStatus("succeeded");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("order_uuid", UUID.randomUUID().toString());
        metadata.put("idempotency_key", "idem-" + UUID.randomUUID());
        payload.setMetadata(metadata);
        return payload;
    }

    public static StripeWebhookEventDto aValidPaymentSucceededEvent() {
        StripeWebhookEventDto event = new StripeWebhookEventDto();
        event.setId("evt_" + UUID.randomUUID());
        event.setCreated(System.currentTimeMillis() / 1000);
        event.setLivemode(false);
        event.setType(StripeEventType.PAYMENT_INTENT_SUCCEEDED);

        StripeWebhookEventDto.DataPayload data = new StripeWebhookEventDto.DataPayload();
        data.setPaymentIntentPayload(aValidPaymentIntentPayload());
        event.setData(data);
        return event;
    }
}
