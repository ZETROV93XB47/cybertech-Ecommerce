package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.services.core.AbstractNotification;
import com.novatech.cybertech.services.core.NotificationProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@NotificationTypeHandler(NotificationType.SHIPPING_CONFIRMATION)
public class ShippingConfirmationNotification extends AbstractNotification {
    public ShippingConfirmationNotification(NotificationProcessor notificationProcessor) {
        super(notificationProcessor);
    }

    @Override
    public void sendNotification(final NotificationContext notificationContext) {

        OrderEntity savedOrder = (OrderEntity) notificationContext.getPayload();

        final Map<String, Object> model = new HashMap<>();

        model.put("userName", savedOrder.getUserEntity().getFirstName());
        model.put("orderId", savedOrder.getId());
        model.put("amount", savedOrder.getTotalAmount());

        //notificationContext.withNotificationProcessorData(notificationProcessorData);

        notificationProcessor.sendMessage(notificationContext);
    }
}
