package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.NotificationContext;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public abstract class AbstractNotification {
    public abstract void sendNotification(final NotificationContext notificationContext, final NotificationProcessor notificationProcessor);
}
