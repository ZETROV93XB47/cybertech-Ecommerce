package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.services.core.AbstractNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStrategyFactory {
    private final Map<NotificationType, AbstractNotification> notificationTypeStrategyMap;

    public AbstractNotification getStrategy(final NotificationType type) {
        return notificationTypeStrategyMap.get(type);
    }

}
