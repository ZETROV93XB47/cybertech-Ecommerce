package com.novatech.cybertech.dto.data;


import com.novatech.cybertech.entities.enums.NotificationType;
import lombok.*;

import java.util.Map;

//TODO: à retirer
@Setter

@With
@Getter
@Builder
@ToString
@EqualsAndHashCode
public class NotificationContext {
    private  Object payload;
    private final UserContactDto user;
    private final String message;
    private Map<String, Object> data;
    private final NotificationType notificationType;
}

