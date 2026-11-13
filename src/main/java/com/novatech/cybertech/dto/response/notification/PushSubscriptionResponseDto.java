package com.novatech.cybertech.dto.response.notification;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PushSubscriptionResponseDto(UUID uuid) {
}
