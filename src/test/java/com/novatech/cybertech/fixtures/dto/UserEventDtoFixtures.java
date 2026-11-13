package com.novatech.cybertech.fixtures.dto;

import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.entities.enums.UserEventType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tiny DTO factories for the user-event surface.
 */
public final class UserEventDtoFixtures {

    private UserEventDtoFixtures() {
    }

    public static UserEventDto aValidUserEvent() {
        return aValidUserEventBuilder().build();
    }

    public static UserEventDto.UserEventDtoBuilder aValidUserEventBuilder() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "web");
        return UserEventDto.builder()
                .userId(UUID.randomUUID().toString())
                .eventType(UserEventType.VIEW)
                .productId(UUID.randomUUID().toString())
                .sessionId(UUID.randomUUID().toString())
                .metadata(metadata);
    }
}
