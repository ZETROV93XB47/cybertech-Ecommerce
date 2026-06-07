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

/**
 * Bridge dispatcher that pairs a {@link AbstractNotification} (selected by
 * {@link com.novatech.cybertech.entities.enums.NotificationType}) with a
 * {@link NotificationProcessor} (selected by the user's preferred communication channel) and
 * fires the notification.
 *
 * <p>The dispatcher is the single fan-in point used by {@link
 * com.novatech.cybertech.listener.NotificationListener} and
 * {@link com.novatech.cybertech.listener.OrderEventListener} to send user-facing notifications,
 * ensuring strategy selection logic is not duplicated across listeners.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationStrategyFactory notificationStrategyFactory;
    private final NotificationProcessorStrategyFactory notificationProcessorStrategyFactory;

    //We can directly use the two following maps here
    //private final Map<NotificationType, Notification> notificationStrategies;
    //private final Map<CommunicationType, NotificationProcessor> processorStrategies;

    /**
     * Resolves the notification strategy and processor for the given context, then fires the
     * notification through the bridge ({@link AbstractNotification#sendNotification}).
     *
     * @param context the fully populated notification context (type, channel, payload, user)
     * @throws NoStrategyFoundForProcessingTheRequest when either the notification strategy or
     *                                                the processor strategy cannot be resolved
     *                                                for the requested type/channel pair
     */
    public void dispatch(final NotificationContext<?> context) throws NoStrategyFoundForProcessingTheRequest {

        final UserContactDto user = context.getUser();

        final AbstractNotification notification = notificationStrategyFactory.getStrategy(context.getNotificationType());
        final NotificationProcessor processor = notificationProcessorStrategyFactory.getStrategy(user.getDefaultCommunicationChanel());

        if (notification == null || processor == null) {
            log.error("Aucune stratégie trouvée pour NotificationType={} ou CommunicationType={}", context.getNotificationType(), user.getDefaultCommunicationChanel());
            throw new NoStrategyFoundForProcessingTheRequest("Aucune stratégie trouvée pour NotificationType=" + context.getNotificationType() + " ou CommunicationType=" + user.getDefaultCommunicationChanel());
        }

        // Bridge: dynamically inject the processor into the notification at dispatch time.
        notification.sendNotification(context, processor);
    }
}
