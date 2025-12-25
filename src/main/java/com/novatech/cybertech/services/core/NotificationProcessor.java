package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.NotificationContext;

public interface NotificationProcessor {
    void sendMessage(final NotificationContext notificationContext);
}
