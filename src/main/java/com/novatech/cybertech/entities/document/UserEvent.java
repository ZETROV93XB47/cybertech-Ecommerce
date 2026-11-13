package com.novatech.cybertech.entities.document;

import com.novatech.cybertech.entities.enums.UserEventType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_events")
public class UserEvent {

    @Id
    private String id;

    private String userId;          // ID interne ou keycloakId

    private String sessionId;       // ID de session frontend

    private UserEventType eventType;

    private String productId;          // productId

    private Instant timestamp;

    private Map<String, Object> metadata; // category, brand, price, referrer, device, etc.
}