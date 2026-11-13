package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.services.core.AbstractNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@NotificationTypeHandler(NotificationType.PAYMENT_CONFIRMATION)
public class PaymentConfirmationNotification extends AbstractNotification {

    private static final String USER_NAME = "userName";
    private static final String ORDER_ID = "orderId";
    private static final String AMOUNT = "amount";

    @Override
    protected void prepareContext(final NotificationContext<?> context) {
        final OrderConfirmationPayload payload = (OrderConfirmationPayload) context.getPayload();
        // subject and templatePath are already set by NotificationListener at context construction time.
        context.setTemplateVariables(Map.of(
                USER_NAME, payload.getUserContactDto().getName(),
                ORDER_ID, payload.getOrderUuid(),
                AMOUNT, payload.getTotalAmount()
        ));
    }
}
