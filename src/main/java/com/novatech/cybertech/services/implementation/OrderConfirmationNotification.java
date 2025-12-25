package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.services.core.AbstractNotification;
import com.novatech.cybertech.services.core.NotificationProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Primary
@Service
@NotificationTypeHandler(NotificationType.ORDER_CONFIRMATION)
public class OrderConfirmationNotification extends AbstractNotification {

    public OrderConfirmationNotification(NotificationProcessor notificationProcessor) {
        super(notificationProcessor);
    }

    @Override
    public void sendNotification(final NotificationContext notificationContext) {

        OrderEventDto orderEventDto = (OrderEventDto) notificationContext.getPayload();

        final Map<String, Object> model = new HashMap<>();

        model.put("userName", orderEventDto.getUserContactDto().getName());
        model.put("orderId", orderEventDto.getOrderUuid());
        model.put("amount", orderEventDto.getTotalAmount());
        model.put("email", orderEventDto.getUserContactDto().getEmail());

        notificationContext.setData(model);

        notificationProcessor.sendMessage(notificationContext);
    }
}
