package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationSubject;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.services.core.AbstractNotification;
import com.novatech.cybertech.services.core.NotificationProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NotificationTypeHandler(NotificationType.ORDER_CONFIRMATION)
public class OrderConfirmationNotification extends AbstractNotification {


    private static final String USER_NAME = "userName";
    private static final String ORDER_ID = "orderId";
    private static final String AMOUNT = "amount";
    private static final String EMAIL = "email";
    private static final String ORDER_STATUS = "orderStatus";
    private static final String TITLE = "title";
    private static final String THEME_COLOR = "themeColor";
    private static final String THEME_BACKGROUND_COLOR = "themeBackgroundColor";
    private static final String THEME_BORDER_COLOR = "themeBorderColor";

    @Override
    public void sendNotification(final NotificationContext notificationContext, final NotificationProcessor notificationProcessor) {

        OrderConfirmationPayload orderConfirmationPayload = (OrderConfirmationPayload) notificationContext.getPayload();

        notificationContext.getData().put(USER_NAME, orderConfirmationPayload.getUserContactDto().getName());
        notificationContext.getData().put(ORDER_ID, orderConfirmationPayload.getOrderUuid());
        notificationContext.getData().put(AMOUNT, orderConfirmationPayload.getTotalAmount());
        notificationContext.getData().put(EMAIL, orderConfirmationPayload.getUserContactDto().getEmail());
        notificationContext.getData().put(ORDER_STATUS, orderConfirmationPayload.getOrderStatus());

        String subject = formattingNotification(notificationContext, orderConfirmationPayload);

        notificationContext.setSubject(subject);
        notificationContext.setTemplatePath(EmailTemplateType.ORDER_CONFIRMATION.getTemplatePath());

        notificationProcessor.sendMessage(notificationContext);
    }



    private static String formattingNotification(NotificationContext notificationContext, OrderConfirmationPayload orderConfirmationPayload) {
        // Détermination dynamique du sujet et du titre en fonction du paiement
        log.info("Préparation de la notification Email pour la confirmation de commande : {}", notificationContext);

        String subject;
        String title;
        String themeColor;
        String themeBackgroundColor;
        String themeBorderColor;

        if (PaymentAttemptStatus.SUCCESS.equals(orderConfirmationPayload.getPaymentAttemptStatus())) {
            subject = NotificationSubject.ORDER_CONFIRMATION.getSubject();
            title = "Confirmation de votre commande";
            themeColor = "#27ae60"; // Vert
            themeBackgroundColor = "#eafaf1";
            themeBorderColor = "#d5f5e3";
        }
        else if (PaymentAttemptStatus.FAILED.equals(orderConfirmationPayload.getPaymentAttemptStatus()) || PaymentAttemptStatus.CANCELED.equals(orderConfirmationPayload.getPaymentAttemptStatus())) {
            subject = "Commande enregistrée - Paiement échoué";
            title = "Commande créée mais paiement non abouti";
            themeColor = "#e74c3c"; // Rouge
            themeBackgroundColor = "#fdedec";
            themeBorderColor = "#fadbd8";
        }
        else {
            subject = "Commande enregistrée - Paiement en attente";
            title = "Commande créée - Paiement en cours de validation";
            themeColor = "#f39c12"; // Orange
            themeBackgroundColor = "#fef9e7";
            themeBorderColor = "#fdebd0";
        }

        notificationContext.getData().put(TITLE, title);
        notificationContext.getData().put(THEME_COLOR, themeColor);
        notificationContext.getData().put(THEME_BACKGROUND_COLOR, themeBackgroundColor);
        notificationContext.getData().put(THEME_BORDER_COLOR, themeBorderColor);

        return subject;
    }


}
