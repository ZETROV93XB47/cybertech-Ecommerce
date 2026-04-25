package com.novatech.cybertech.dto.request.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novatech.cybertech.entities.enums.UserEventType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * BUG-SPOOF-D5: {@code userId} is no longer client-controlled. The controller derives it from
 * the JWT subject; Jackson is instructed to ignore inbound {@code userId} via
 * {@link JsonIgnoreProperties} so a malicious client cannot pre-populate it. {@code @NotBlank}
 * validation was removed for the same reason — the field is server-set after deserialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(value = "userId", allowGetters = true)
public class UserEventDto {

    private String userId;

    @NotNull(message = "eventType is required")
    private UserEventType eventType;

    @NotNull(message = "productId is required")
    private String productId; // Peut être null selon le type d'event, validation conditionnelle possible ou laissée au service si complexe
    @NotNull(message = "sessionId is required")
    private String sessionId;

    private Map<String, Object> metadata;
}