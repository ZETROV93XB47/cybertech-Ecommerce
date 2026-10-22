package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.notification.PushSubscriptionRequestDto;
import com.novatech.cybertech.dto.response.notification.PushSubscriptionResponseDto;

import java.util.UUID;

public interface PushSubscriptionService {

    PushSubscriptionResponseDto register(String keycloakId, PushSubscriptionRequestDto request);

    void delete(String keycloakId, UUID subscriptionUuid);
}
