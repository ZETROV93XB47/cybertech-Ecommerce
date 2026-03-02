package com.novatech.cybertech.dto.request.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.novatech.cybertech.entities.enums.StripeEventType;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeWebhookEventDto {

    private String id;
    private Long created;
    private Boolean livemode;
    private StripeEventType type;
    private DataPayload data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataPayload {

        @JsonProperty("object")
        private PaymentIntentPayload paymentIntentPayload;
    }
}