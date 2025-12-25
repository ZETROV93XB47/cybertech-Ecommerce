package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.NotificationContext;
import lombok.AllArgsConstructor;
import lombok.Setter;

@Setter
@AllArgsConstructor
public abstract class AbstractNotification {

    protected NotificationProcessor notificationProcessor;

    public AbstractNotification setNotificationProcessorAndRun(final NotificationProcessor notificationProcessor) {
        this.notificationProcessor = notificationProcessor;
        return this;
    }

    public AbstractNotification withNotificationProcessor(final NotificationProcessor notificationProcessor) {
        this.notificationProcessor = notificationProcessor;
        return this;
    }

    public abstract void sendNotification(final NotificationContext notificationContext);
}
