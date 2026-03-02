package com.novatech.cybertech.services.core;

import com.stripe.model.Event;

public interface PaymentWebhookService {
    void handleEvent(Event event, String eventPayload);
}
