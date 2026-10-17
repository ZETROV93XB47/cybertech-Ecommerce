package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.NotificationContext;

public abstract class AbstractNotification {

    public final void sendNotification(final NotificationContext<?> context, final NotificationProcessor processor) {
        prepareContext(context);
        processor.sendMessage(context);
    }

    /**
     * Implementors set {@code context.subject}, {@code context.templatePath} (when dynamic), and
     * {@code context.templateVariables} by calling {@code context.setTemplateVariables(Map.of(...))}.
     * No mutation of other fields is expected.
     */
    protected abstract void prepareContext(NotificationContext<?> context);
}
