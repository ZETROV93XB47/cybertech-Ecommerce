package com.novatech.cybertech.dto.data;


import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationType;
import lombok.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class NotificationContext<T extends NotificationPayload> {
    private T payload;
    private String subject;
    private UserContactDto user;
    private String templatePath;
    private NotificationType notificationType;
    private CommunicationChanel communicationChanel;
    /**
     * Web Push only: the one browser subscription this notification targets. Push is fanned out
     * one notification per subscription so a failing browser is retried (and audited) alone.
     */
    private UUID pushSubscriptionUuid;
    @Builder.Default
    private Map<String, Object> templateVariables = new HashMap<>();
}
