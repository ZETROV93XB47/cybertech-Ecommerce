package com.novatech.cybertech.services.core;

import java.util.UUID;

public interface WebPushDeliveryService {

    boolean hasSubscriptions(String keycloakId);

    void sendToUser(String keycloakId, String title, String body, UUID orderUuid);
}
