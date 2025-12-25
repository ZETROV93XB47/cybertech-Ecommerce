package com.novatech.cybertech.dispatcher;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.factory.NotificationProcessorStrategyFactory;
import com.novatech.cybertech.factory.NotificationStrategyFactory;
import com.novatech.cybertech.services.core.AbstractNotification;
import com.novatech.cybertech.services.core.NotificationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationStrategyFactory notificationStrategyFactory;
    private final NotificationProcessorStrategyFactory notificationProcessorStrategyFactory;

    //We can directly use the two following maps here
    //private final Map<NotificationType, Notification> notificationStrategies;
    //private final Map<CommunicationType, NotificationProcessor> processorStrategies;

    public void dispatch(final NotificationContext context) {

        final UserContactDto user = context.getUser();

        final AbstractNotification notification = notificationStrategyFactory.getStrategy(context.getNotificationType());
        final NotificationProcessor processor = notificationProcessorStrategyFactory.getStrategy(user.getDefaultCommunicationChanel());

        if (notification == null || processor == null) {
            log.error("Aucune stratégie trouvée pour NotificationType={} ou CommunicationType={}", context.getNotificationType(), user.getDefaultCommunicationChanel());
            throw new NoStrategyFoundForProcessingTheRequest("Aucune stratégie trouvée pour NotificationType=" + context.getNotificationType() + " ou CommunicationType=" + user.getDefaultCommunicationChanel());
        }

        // Injection dynamique du processor dans la notification (Bridge)
        //notification.setNotificationProcessor(processor);
        notification.withNotificationProcessor(processor).sendNotification(context);
    }
}
