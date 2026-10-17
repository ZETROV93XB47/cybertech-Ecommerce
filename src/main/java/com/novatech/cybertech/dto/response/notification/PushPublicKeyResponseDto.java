package com.novatech.cybertech.dto.response.notification;

import lombok.Builder;

@Builder
public record PushPublicKeyResponseDto(String publicKey) {
}
