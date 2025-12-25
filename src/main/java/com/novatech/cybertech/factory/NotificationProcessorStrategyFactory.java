package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.services.core.NotificationProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationProcessorStrategyFactory {
    private final Map<CommunicationChanel, NotificationProcessor> notificationProcessorTypeStrategyMap;

    public NotificationProcessor getStrategy(CommunicationChanel type) {
        return notificationProcessorTypeStrategyMap.get(type);
    }
}
