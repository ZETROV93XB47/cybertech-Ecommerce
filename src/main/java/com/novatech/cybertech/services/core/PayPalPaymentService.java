package com.novatech.cybertech.services.core;

import java.util.UUID;

public interface PayPalPaymentService {
    void onSuccess(final UUID orderUUID);

    void onFailure();
}
