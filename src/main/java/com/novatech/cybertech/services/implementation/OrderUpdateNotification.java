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

/**
 * E-mail sent on {@link NotificationType#ORDER_UPDATE}: after {@code updateOrder} (the new total
 * was charged as a complement or partly refunded) and after {@code retryPayment}. The payload's
 * {@code paymentAttemptStatus} is the outcome of that payment or refund, which picks the wording.
 * The order status is shown raw (enum name), like in the order-confirmation e-mail.
 */
@Slf4j
@Service
@NotificationTypeHandler(NotificationType.ORDER_UPDATE)
public class OrderUpdateNotification extends AbstractNotification {

    private static final String USER_NAME = "userName";
    private static final String ORDER_ID = "orderId";
    private static final String AMOUNT = "amount";
    private static final String EMAIL = "email";
    private static final String ORDER_STATUS = "orderStatus";
    private static final String TITLE = "title";
    private static final String THEME_COLOR = "themeColor";
    private static final String THEME_BACKGROUND_COLOR = "themeBackgroundColor";
    private static final String THEME_BORDER_COLOR = "themeBorderColor";
    private static final String NEXT_STEP = "nextStep";

    @Override
    protected void prepareContext(final NotificationContext<?> context) {
        final OrderConfirmationPayload payload = (OrderConfirmationPayload) context.getPayload();
        final UpdateFormat format = UpdateFormat.of(payload.getPaymentAttemptStatus());

        log.info("Preparing order update notification, paymentStatus={}", payload.getPaymentAttemptStatus());

        context.setSubject(format.subject());
        context.setTemplatePath(EmailTemplateType.ORDER_UPDATE.getTemplatePath());
        context.setTemplateVariables(Map.of(
                USER_NAME, payload.getUserContactDto().getName(),
                ORDER_ID, payload.getOrderUuid(),
                AMOUNT, payload.getTotalAmount(),
                EMAIL, payload.getUserContactDto().getEmail(),
                ORDER_STATUS, payload.getOrderStatus(),
                TITLE, format.title(),
                THEME_COLOR, format.themeColor(),
                THEME_BACKGROUND_COLOR, format.themeBackgroundColor(),
                THEME_BORDER_COLOR, format.themeBorderColor(),
                NEXT_STEP, format.nextStep()
        ));
    }

    private record UpdateFormat(
            String subject,
            String title,
            String themeColor,
            String themeBackgroundColor,
            String themeBorderColor,
            String nextStep
    ) {
        static UpdateFormat of(final PaymentAttemptStatus status) {
            if (PaymentAttemptStatus.FAILED.equals(status) || PaymentAttemptStatus.CANCELED.equals(status)) {
                return new UpdateFormat(
                        "Commande mise à jour - Paiement échoué",
                        "Votre commande a été mise à jour, mais le paiement n'a pas abouti",
                        "#e74c3c", "#fdedec", "#fadbd8",
                        "Votre paiement n'a pas pu être validé. Vous pouvez le relancer depuis votre espace client."
                );
            }
            if (PaymentAttemptStatus.SUCCESS.equals(status)) {
                return new UpdateFormat(
                        NotificationSubject.ORDER_UPDATE.getSubject(),
                        "Votre commande a été mise à jour",
                        "#27ae60", "#eafaf1", "#d5f5e3",
                        "La modification est prise en compte. Vous recevrez un nouvel email dès que votre commande sera expédiée."
                );
            }
            return new UpdateFormat(
                    "Commande mise à jour - Paiement en attente",
                    "Votre commande a été mise à jour - Paiement en cours de validation",
                    "#f39c12", "#fef9e7", "#fdebd0",
                    "Vous recevrez un nouvel email dès que votre paiement sera confirmé."
            );
        }
    }
}
