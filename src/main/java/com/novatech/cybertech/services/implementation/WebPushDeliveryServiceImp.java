package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.entities.PushSubscriptionEntity;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.exceptions.PushServiceNotConfiguredException;
import com.novatech.cybertech.repositories.PushSubscriptionRepository;
import com.novatech.cybertech.services.core.SensitiveDataEncryptionService;
import com.novatech.cybertech.services.core.WebPushDeliveryService;
import com.novatech.cybertech.services.core.WebPushGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushDeliveryServiceImp implements WebPushDeliveryService {

    private static final int HTTP_GONE = 410;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 300;
    private static final String TITLE_KEY = "title";
    private static final String BODY_KEY = "body";
    private static final String URL_KEY = "url";
    private static final String ORDER_UUID_KEY = "orderUuid";
    private static final String ORDER_DETAILS_PATH = "/account/orders/";

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final SensitiveDataEncryptionService encryptionService;
    private final WebPushGateway webPushGateway;
    private final WebPushProperties webPushProperties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean hasSubscriptions(final String keycloakId) {
        return pushSubscriptionRepository.existsByUserEntity_KeycloakId(keycloakId);
    }

    @Override
    public void sendToUser(final String keycloakId, final String title, final String body, final UUID orderUuid) {
        final List<PushSubscriptionEntity> subscriptions =
                pushSubscriptionRepository.findAllByUserEntity_KeycloakId(keycloakId);
        if (subscriptions.isEmpty()) {
            return;
        }
        final List<PushSubscriptionEntity> activeSubscriptions = subscriptions.stream()
                .filter(subscription -> !isExpired(subscription))
                .toList();
        subscriptions.stream()
                .filter(WebPushDeliveryServiceImp::isExpired)
                .forEach(pushSubscriptionRepository::delete);
        if (activeSubscriptions.isEmpty()) {
            return;
        }
        if (!webPushProperties.isConfigured()) {
            throw new PushServiceNotConfiguredException();
        }

        final String payload = serializePayload(title, body, orderUuid);
        final List<NotificationDeliveryException> failures = new ArrayList<>();

        activeSubscriptions.forEach(subscription -> {
            try {
                final int statusCode = webPushGateway.send(
                        encryptionService.decrypt(subscription.getEndpointEncrypted()),
                        encryptionService.decrypt(subscription.getP256dhEncrypted()),
                        encryptionService.decrypt(subscription.getAuthEncrypted()),
                        payload);
                if (statusCode == HTTP_GONE || statusCode == HTTP_NOT_FOUND) {
                    pushSubscriptionRepository.delete(subscription);
                } else if (statusCode < HTTP_SUCCESS_MIN || statusCode >= HTTP_SUCCESS_MAX) {
                    failures.add(new NotificationDeliveryException(
                            "Web Push service returned HTTP " + statusCode));
                }
            } catch (NotificationDeliveryException exception) {
                failures.add(exception);
            }
        });

        if (!failures.isEmpty()) {
            final NotificationDeliveryException failure = failures.removeFirst();
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static boolean isExpired(final PushSubscriptionEntity subscription) {
        return subscription.getExpirationTime() != null
                && !subscription.getExpirationTime().isAfter(Instant.now());
    }

    private String serializePayload(final String title, final String body, final UUID orderUuid) {
        final LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put(TITLE_KEY, title);
        payload.put(BODY_KEY, body);
        payload.put(URL_KEY, ORDER_DETAILS_PATH + orderUuid);
        payload.put(ORDER_UUID_KEY, orderUuid);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to serialize Web Push payload", exception);
        }
    }
}
