package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
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

        OrderEventDto orderEventDto = (OrderEventDto) notificationContext.getData().get("orderEventDto");

        final Map<String, Object> model = new HashMap<>();

        model.put("userName", orderEventDto.getUserContactDto().getName());
        model.put("orderId", orderEventDto.getOrderUuid());
        model.put("amount", orderEventDto.getTotalAmount());
        model.put("email", orderEventDto.getUserContactDto().getEmail());
        model.put("orderStatus", orderEventDto.getOrderStatus());

        // Détermination dynamique du sujet et du titre en fonction du paiement
        String subject;
        String title;
        String themeColor;
        String themeBackgroundColor;
        String themeBorderColor;

        if (PaymentAttemptStatus.SUCCESS.equals(orderEventDto.getPaymentAttemptStatus())) {
            subject = EmailTemplateType.ORDER_CONFIRMATION.getSubject();
            title = "Confirmation de votre commande";
            themeColor = "#27ae60"; // Vert
            themeBackgroundColor = "#eafaf1";
            themeBorderColor = "#d5f5e3";
        } else if (PaymentAttemptStatus.FAILED.equals(orderEventDto.getPaymentAttemptStatus()) || PaymentAttemptStatus.CANCELED.equals(orderEventDto.getPaymentAttemptStatus())) {
            subject = "Commande enregistrée - Paiement échoué";
            title = "Commande créée mais paiement non abouti";
            themeColor = "#e74c3c"; // Rouge
            themeBackgroundColor = "#fdedec";
            themeBorderColor = "#fadbd8";
        } else {
            subject = "Commande enregistrée - Paiement en attente";
            title = "Commande créée - Paiement en cours de validation";
            themeColor = "#f39c12"; // Orange
            themeBackgroundColor = "#fef9e7";
            themeBorderColor = "#fdebd0";
        }

        model.put("title", title);
        model.put("themeColor", themeColor);
        model.put("themeBackgroundColor", themeBackgroundColor);
        model.put("themeBorderColor", themeBorderColor);

        notificationContext.setSubject(subject);
        notificationContext.setTemplatePath(EmailTemplateType.ORDER_CONFIRMATION.getTemplatePath());

        notificationContext.setData(model);

        notificationProcessor.sendMessage(notificationContext);
    }
}
