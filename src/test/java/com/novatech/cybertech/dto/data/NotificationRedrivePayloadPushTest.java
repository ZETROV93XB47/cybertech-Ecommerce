package com.novatech.cybertech.dto.data;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A push notification is audited per browser: the redrive snapshot must keep the targeted
 * subscription, otherwise a batch redrive could not tell which browser to retry.
 */
class NotificationRedrivePayloadPushTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void pushSubscriptionSurvivesTheJsonRoundTripOfTheRedriveSnapshot() {
        final UUID subscriptionUuid = UUID.randomUUID();
        final UUID orderUuid = UUID.randomUUID();
        final NotificationContext<OrderConfirmationPayload> context = NotificationContext.<OrderConfirmationPayload>builder()
                .notificationType(NotificationType.PAYMENT_CONFIRMATION)
                .communicationChanel(CommunicationChanel.PUSH_NOTIFICATION)
                .pushSubscriptionUuid(subscriptionUuid)
                .user(UserContactDto.builder().keycloakId("kc-user").build())
                .payload(OrderConfirmationPayload.builder().orderUuid(orderUuid).build())
                .build();

        final String json = objectMapper.writeValueAsString(NotificationRedrivePayload.from(context));
        final NotificationContext<NotificationPayload> rebuilt =
                objectMapper.readValue(json, NotificationRedrivePayload.class).toNotificationContext();

        assertThat(rebuilt.getPushSubscriptionUuid()).isEqualTo(subscriptionUuid);
        assertThat(rebuilt.getCommunicationChanel()).isEqualTo(CommunicationChanel.PUSH_NOTIFICATION);
        assertThat(rebuilt.getUser().getKeycloakId()).isEqualTo("kc-user");
        assertThat(((OrderConfirmationPayload) rebuilt.getPayload()).getOrderUuid()).isEqualTo(orderUuid);
    }
}
