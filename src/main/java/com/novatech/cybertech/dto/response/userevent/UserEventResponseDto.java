package com.novatech.cybertech.dto.response.userevent;

import com.novatech.cybertech.entities.enums.UserEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Curated public projection of a {@link com.novatech.cybertech.entities.document.UserEvent}.
 * Decouples the API contract from the MongoDB persistence schema and avoids leaking internal
 * fields if the document evolves (e.g. additional moderation/scoring fields).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserEventResponseDto {

    private String id;

    private String userId;

    private String sessionId;

    private UserEventType eventType;

    private String productId;

    private Instant timestamp;

    private Map<String, Object> metadata;
}
