package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationSubject;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.services.core.AbstractNotification;
import com.novatech.cybertech.services.core.NotificationProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NotificationTypeHandler(NotificationType.SHIPPING_CONFIRMATION)
public class ShippingConfirmationNotification extends AbstractNotification {

    private static final String USER_NAME_KEY = "userName";
    private static final String ORDER_ID_KEY = "orderId";
    private static final String SHIPPING_PROVIDER_KEY = "shippingProvider";
    private static final String SHIPPING_TYPE_KEY = "shippingType";


    @Override
    public void sendNotification(final NotificationContext notificationContext, final NotificationProcessor notificationProcessor) {

        final ShippingConfirmationPayload shippingConfirmationPayload = (ShippingConfirmationPayload) notificationContext.getPayload();

        notificationContext.getData().put(USER_NAME_KEY, shippingConfirmationPayload.getUserName());
        notificationContext.getData().put(ORDER_ID_KEY, shippingConfirmationPayload.getOrderUuid());
        notificationContext.getData().put(SHIPPING_PROVIDER_KEY, shippingConfirmationPayload.getShippingProvider());
        notificationContext.getData().put(SHIPPING_TYPE_KEY, shippingConfirmationPayload.getShippingType());

        // Configuration du sujet et du template via l'Enum
        notificationContext.setSubject(NotificationSubject.SHIPPING_CONFIRMATION.getSubject());
        notificationContext.setTemplatePath(EmailTemplateType.SHIPPING_CONFIRMATION.getTemplatePath());

        notificationProcessor.sendMessage(notificationContext);
    }
}
