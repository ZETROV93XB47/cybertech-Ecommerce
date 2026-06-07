package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.services.core.AbstractNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@NotificationTypeHandler(NotificationType.SHIPPING_CONFIRMATION)
public class ShippingConfirmationNotification extends AbstractNotification {

    private static final String USER_NAME_KEY = "userName";
    private static final String ORDER_ID_KEY = "orderId";
    private static final String SHIPPING_PROVIDER_KEY = "shippingProvider";
    private static final String SHIPPING_TYPE_KEY = "shippingType";

    @Override
    protected void prepareContext(final NotificationContext<?> context) {
        final ShippingConfirmationPayload payload = (ShippingConfirmationPayload) context.getPayload();
        // subject and templatePath are already set by NotificationListener at context construction time.
        context.setTemplateVariables(Map.of(
                USER_NAME_KEY, payload.getUserName(),
                ORDER_ID_KEY, payload.getOrderUuid(),
                SHIPPING_PROVIDER_KEY, payload.getShippingProvider(),
                SHIPPING_TYPE_KEY, payload.getShippingType()
        ));
    }
}
