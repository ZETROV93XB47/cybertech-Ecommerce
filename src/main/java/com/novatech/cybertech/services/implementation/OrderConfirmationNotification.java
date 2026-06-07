package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderConfirmationPayload;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationSubject;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.services.core.AbstractNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

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
    protected void prepareContext(final NotificationContext<?> context) {
        final OrderConfirmationPayload payload = (OrderConfirmationPayload) context.getPayload();
        final OrderNotificationFormat format = OrderNotificationFormat.of(payload.getPaymentAttemptStatus());

        log.info("Preparing order confirmation notification, paymentStatus={}", payload.getPaymentAttemptStatus());

        context.setSubject(format.subject());
        context.setTemplatePath(EmailTemplateType.ORDER_CONFIRMATION.getTemplatePath());
        context.setTemplateVariables(Map.of(
                USER_NAME, payload.getUserContactDto().getName(),
                ORDER_ID, payload.getOrderUuid(),
                AMOUNT, payload.getTotalAmount(),
                EMAIL, payload.getUserContactDto().getEmail(),
                ORDER_STATUS, payload.getOrderStatus(),
                TITLE, format.title(),
                THEME_COLOR, format.themeColor(),
                THEME_BACKGROUND_COLOR, format.themeBackgroundColor(),
                THEME_BORDER_COLOR, format.themeBorderColor()
        ));
    }

    private record OrderNotificationFormat(
            String subject,
            String title,
            String themeColor,
            String themeBackgroundColor,
            String themeBorderColor
    ) {
        static OrderNotificationFormat of(final PaymentAttemptStatus status) {
            if (PaymentAttemptStatus.SUCCESS.equals(status)) {
                return new OrderNotificationFormat(
                        NotificationSubject.ORDER_CONFIRMATION.getSubject(),
                        "Confirmation de votre commande",
                        "#27ae60", "#eafaf1", "#d5f5e3"
                );
            }
            if (PaymentAttemptStatus.FAILED.equals(status) || PaymentAttemptStatus.CANCELED.equals(status)) {
                return new OrderNotificationFormat(
                        "Commande enregistrée - Paiement échoué",
                        "Commande créée mais paiement non abouti",
                        "#e74c3c", "#fdedec", "#fadbd8"
                );
            }
            return new OrderNotificationFormat(
                    "Commande enregistrée - Paiement en attente",
                    "Commande créée - Paiement en cours de validation",
                    "#f39c12", "#fef9e7", "#fdebd0"
            );
        }
    }
}
