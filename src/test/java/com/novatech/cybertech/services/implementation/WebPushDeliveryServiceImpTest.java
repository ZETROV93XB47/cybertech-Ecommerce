package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.entities.PushSubscriptionEntity;
import com.novatech.cybertech.repositories.PushSubscriptionRepository;
import com.novatech.cybertech.services.core.SensitiveDataEncryptionService;
import com.novatech.cybertech.services.core.WebPushGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WebPushDeliveryServiceImpTest {

    private static final String USER_ID = "kc-user";
    private static final UUID ORDER_UUID = UUID.randomUUID();

    @Mock
    private PushSubscriptionRepository subscriptionRepository;
    @Mock
    private SensitiveDataEncryptionService encryptionService;
    @Mock
    private WebPushGateway webPushGateway;
    @Mock
    private WebPushProperties properties;

    private WebPushDeliveryServiceImp service;

    @BeforeEach
    void setUp() {
        service = new WebPushDeliveryServiceImp(
                subscriptionRepository,
                encryptionService,
                webPushGateway,
                properties,
                new ObjectMapper());
        lenient().when(encryptionService.decrypt(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0, String.class).substring("enc:".length()));
    }

    @Test
    void sendBuildsGenericPayloadAndUsesDecryptedSubscriptionData() {
        when(subscriptionRepository.findAllByUserEntity_KeycloakId(USER_ID))
                .thenReturn(List.of(subscription(null)));
        when(properties.isConfigured()).thenReturn(true);
        when(webPushGateway.send(anyString(), anyString(), anyString(), anyString())).thenReturn(201);

        service.sendToUser(USER_ID, "Paiement confirmé", "Le paiement de votre commande a été confirmé.", ORDER_UUID);

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(webPushGateway).send(
                org.mockito.ArgumentMatchers.eq("https://fcm.googleapis.com/fcm/send/device"),
                org.mockito.ArgumentMatchers.eq("p256dh"),
                org.mockito.ArgumentMatchers.eq("auth"),
                payload.capture());
        assertThat(payload.getValue())
                .contains("\"title\":\"Paiement confirmé\"")
                .contains("\"body\":\"Le paiement de votre commande a été confirmé.\"")
                .contains("\"orderUuid\":\"" + ORDER_UUID + "\"")
                .contains("\"url\":\"/account/orders/" + ORDER_UUID + "\"");
    }

    @Test
    void sendDeletesExpiredSubscriptionsWithoutCallingProvider() {
        final PushSubscriptionEntity expired = subscription(Instant.now().minusSeconds(1));
        when(subscriptionRepository.findAllByUserEntity_KeycloakId(USER_ID)).thenReturn(List.of(expired));

        service.sendToUser(USER_ID, "title", "body", ORDER_UUID);

        verify(subscriptionRepository).delete(expired);
        verify(webPushGateway, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendDeletesProviderGoneSubscriptions() {
        final PushSubscriptionEntity subscription = subscription(null);
        when(subscriptionRepository.findAllByUserEntity_KeycloakId(USER_ID)).thenReturn(List.of(subscription));
        when(properties.isConfigured()).thenReturn(true);
        when(webPushGateway.send(anyString(), anyString(), anyString(), anyString())).thenReturn(410);

        service.sendToUser(USER_ID, "title", "body", ORDER_UUID);

        verify(subscriptionRepository).delete(subscription);
    }

    @Test
    void sendRaisesRetryableFailureForProviderServerErrors() {
        when(subscriptionRepository.findAllByUserEntity_KeycloakId(USER_ID))
                .thenReturn(List.of(subscription(null)));
        when(properties.isConfigured()).thenReturn(true);
        when(webPushGateway.send(anyString(), anyString(), anyString(), anyString())).thenReturn(503);

        assertThatThrownBy(() -> service.sendToUser(USER_ID, "title", "body", ORDER_UUID))
                .isInstanceOf(com.novatech.cybertech.exceptions.NotificationDeliveryException.class)
                .hasMessageContaining("503");
    }

    @Test
    void sendDoesNotRequireVapidConfigurationWhenUserHasNoSubscriptions() {
        when(subscriptionRepository.findAllByUserEntity_KeycloakId(USER_ID)).thenReturn(List.of());

        service.sendToUser(USER_ID, "title", "body", ORDER_UUID);

        verify(properties, never()).isConfigured();
        verify(webPushGateway, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendFailsClearlyWhenSubscriptionsExistButVapidIsMissing() {
        when(subscriptionRepository.findAllByUserEntity_KeycloakId(USER_ID))
                .thenReturn(List.of(subscription(null)));
        when(properties.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.sendToUser(USER_ID, "title", "body", ORDER_UUID))
                .isInstanceOf(com.novatech.cybertech.exceptions.PushServiceNotConfiguredException.class);
    }

    private static PushSubscriptionEntity subscription(final Instant expirationTime) {
        return PushSubscriptionEntity.builder()
                .endpointEncrypted("enc:https://fcm.googleapis.com/fcm/send/device")
                .p256dhEncrypted("enc:p256dh")
                .authEncrypted("enc:auth")
                .expirationTime(expirationTime)
                .build();
    }
}
