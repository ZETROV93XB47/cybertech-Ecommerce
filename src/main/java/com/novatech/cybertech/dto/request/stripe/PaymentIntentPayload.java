package com.novatech.cybertech.dto.request.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.stripe.model.PaymentIntent;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentIntentPayload {

    private String id;                       // "pi_..."
    private String object;                   // "payment_intent"
    private Long amount;
    private Long amount_received;
    private String currency;
    private String latest_charge;
    private String payment_method;
    private List<String> payment_method_types;
    private String status;

    private Map<String, String> metadata;    // order_uuid, idempotency_key

    private PaymentIntent.AutomaticPaymentMethods automatic_payment_methods;
}