package com.novatech.cybertech.dto.request.event;

import com.novatech.cybertech.entities.enums.UserEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEventDto {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotNull(message = "eventType is required")
    private UserEventType eventType;

    @NotNull(message = "productId is required")
    private String productId; // Peut être null selon le type d'event, validation conditionnelle possible ou laissée au service si complexe
    @NotNull(message = "sessionId is required")
    private String sessionId;

    private Map<String, Object> metadata;
}