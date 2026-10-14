package com.novatech.cybertech.services.core;

import java.util.List;
import java.util.UUID;

public interface WebPushDeliveryService {

    /**
     * UUIDs of the user's non-expired subscriptions (one per browser). Expired subscriptions are
     * deleted on the way. Each UUID is delivered as its own notification, so a failing browser is
     * retried alone and never re-sends the push to the browsers that already received it.
     */
    List<UUID> findActiveSubscriptionUuids(String keycloakId);

    /**
     * Sends to one subscription of the user. A subscription that no longer exists, has expired or
     * is reported gone by the push service (404/410) is a successful no-op; any other non-2xx
     * answer or transport failure raises a retryable {@code NotificationDeliveryException}.
     */
    void sendToSubscription(String keycloakId, UUID subscriptionUuid, String title, String body, UUID orderUuid);
}
