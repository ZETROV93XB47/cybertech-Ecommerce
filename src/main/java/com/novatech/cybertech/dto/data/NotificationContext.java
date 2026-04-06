package com.novatech.cybertech.dto.data;


import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationType;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

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
    private Map<String, Object> data = new HashMap<>();
}
