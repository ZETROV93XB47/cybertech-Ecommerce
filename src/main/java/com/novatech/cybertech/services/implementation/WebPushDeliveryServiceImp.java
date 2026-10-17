package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.entities.BaseEntity;
import com.novatech.cybertech.entities.PushSubscriptionEntity;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.exceptions.PermanentNotificationFailureException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.novatech.cybertech.config.WebPushProperties.ORDER_UUID_PLACEHOLDER;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushDeliveryServiceImp implements WebPushDeliveryService {

    private static final int HTTP_GONE = 410;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 300;
    private static final int HTTP_REQUEST_TIMEOUT = 408;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR_MIN = 500;
    private static final String PUSH_SERVICE_RETURNED = "Web Push service returned HTTP ";
    private static final String TITLE_KEY = "title";
    private static final String BODY_KEY = "body";
    private static final String URL_KEY = "url";
    private static final String ORDER_UUID_KEY = "orderUuid";
    private static final String SUBSCRIPTION_GONE_LOG = "Push subscription {} no longer exists, skipping delivery";

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final SensitiveDataEncryptionService encryptionService;
    private final WebPushGateway webPushGateway;
    private final WebPushProperties webPushProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<UUID> findActiveSubscriptionUuids(final String keycloakId) {
        final Map<Boolean, List<PushSubscriptionEntity>> byExpiry =
                pushSubscriptionRepository.findAllByUserEntity_KeycloakIdOrderByCreatedAtAsc(keycloakId).stream()
                        .collect(Collectors.partitioningBy(WebPushDeliveryServiceImp::isExpired));
        byExpiry.get(true).forEach(pushSubscriptionRepository::delete);
        return byExpiry.get(false).stream()
                .map(BaseEntity::getUuid)
                .toList();
    }

    @Override
    public void sendToSubscription(final String keycloakId,
                                   final UUID subscriptionUuid,
                                   final String title,
                                   final String body,
                                   final UUID orderUuid) {
        final PushSubscriptionEntity subscription = pushSubscriptionRepository
                .findByUuidAndUserEntity_KeycloakId(subscriptionUuid, keycloakId)
                .orElse(null);
        if (subscription == null) {
            // Unsubscribed (or replaced by a newer browser) between fan-out and delivery / redrive.
            log.info(SUBSCRIPTION_GONE_LOG, subscriptionUuid);
            return;
        }
        if (isExpired(subscription)) {
            pushSubscriptionRepository.delete(subscription);
            return;
        }
        if (!webPushProperties.isConfigured()) {
            throw new PushServiceNotConfiguredException();
        }

        final int statusCode = webPushGateway.send(
                encryptionService.decrypt(subscription.getEndpointEncrypted()),
                encryptionService.decrypt(subscription.getP256dhEncrypted()),
                encryptionService.decrypt(subscription.getAuthEncrypted()),
                serializePayload(title, body, orderUuid));
        if (statusCode >= HTTP_SUCCESS_MIN && statusCode < HTTP_SUCCESS_MAX) {
            return;
        }
        if (statusCode == HTTP_GONE || statusCode == HTTP_NOT_FOUND) {
            pushSubscriptionRepository.delete(subscription);
            return;
        }
        if (isTransient(statusCode)) {
            throw new NotificationDeliveryException(PUSH_SERVICE_RETURNED + statusCode);
        }
        // 400 (malformed), 401/403 (VAPID refused), 413 (payload too large)...: the same request
        // would be rejected again, so it is neither retried nor redriven.
        throw new PermanentNotificationFailureException(PUSH_SERVICE_RETURNED + statusCode);
    }

    /** 408 (timeout), 429 (rate limited) and 5xx may succeed later; any other 4xx will not. */
    private static boolean isTransient(final int statusCode) {
        return statusCode == HTTP_REQUEST_TIMEOUT || statusCode == HTTP_TOO_MANY_REQUESTS || statusCode >= HTTP_SERVER_ERROR_MIN;
    }

    private static boolean isExpired(final PushSubscriptionEntity subscription) {
        return subscription.getExpirationTime() != null
                && !subscription.getExpirationTime().isAfter(Instant.now());
    }

    private String serializePayload(final String title, final String body, final UUID orderUuid) {
        final LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put(TITLE_KEY, title);
        payload.put(BODY_KEY, body);
        payload.put(URL_KEY, webPushProperties.getOrderDetailsUrlTemplate()
                .replace(ORDER_UUID_PLACEHOLDER, orderUuid.toString()));
        payload.put(ORDER_UUID_KEY, orderUuid);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to serialize Web Push payload", exception);
        }
    }
}
