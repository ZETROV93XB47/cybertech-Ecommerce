package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.entities.PushSubscriptionEntity;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.exceptions.PushServiceNotConfiguredException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebPushDeliveryServiceImpTest {

    private static final String USER_ID = "kc-user";
    private static final UUID ORDER_UUID = UUID.randomUUID();
    private static final UUID SUBSCRIPTION_UUID = UUID.randomUUID();
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/device";
    private static final String TITLE = "Paiement confirmé";
    private static final String BODY = "Le paiement de votre commande a été confirmé.";

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
        lenient().when(properties.getOrderDetailsUrlTemplate()).thenReturn("/account/orders/{orderUuid}");
    }

    @Test
    void findActiveSubscriptionUuidsReturnsLiveOnesAndDeletesExpiredOnes() {
        final PushSubscriptionEntity live = subscription(UUID.randomUUID(), null);
        final PushSubscriptionEntity futureExpiry = subscription(UUID.randomUUID(), Instant.now().plusSeconds(3600));
        final PushSubscriptionEntity expired = subscription(UUID.randomUUID(), Instant.now().minusSeconds(1));
        when(subscriptionRepository.findAllByUserEntity_KeycloakIdOrderByCreatedAtAsc(USER_ID))
                .thenReturn(List.of(live, expired, futureExpiry));

        assertThat(service.findActiveSubscriptionUuids(USER_ID))
                .containsExactly(live.getUuid(), futureExpiry.getUuid());
        verify(subscriptionRepository).delete(expired);
        verify(subscriptionRepository, never()).delete(live);
    }

    @Test
    void sendBuildsGenericPayloadForTheTargetedSubscriptionOnly() {
        stubSubscription(subscription(SUBSCRIPTION_UUID, null));
        when(properties.isConfigured()).thenReturn(true);
        when(webPushGateway.send(anyString(), anyString(), anyString(), anyString())).thenReturn(201);

        service.sendToSubscription(USER_ID, SUBSCRIPTION_UUID, TITLE, BODY, ORDER_UUID);

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(webPushGateway).send(eq(ENDPOINT), eq("p256dh"), eq("auth"), payload.capture());
        assertThat(payload.getValue())
                .contains("\"title\":\"" + TITLE + "\"")
                .contains("\"body\":\"" + BODY + "\"")
                .contains("\"orderUuid\":\"" + ORDER_UUID + "\"")
                .contains("\"url\":\"/account/orders/" + ORDER_UUID + "\"");
    }

    @Test
    void sendUsesConfiguredOrderDetailsPath() {
        stubSubscription(subscription(SUBSCRIPTION_UUID, null));
        when(properties.isConfigured()).thenReturn(true);
        when(properties.getOrderDetailsUrlTemplate()).thenReturn("/mon-compte/commandes/{orderUuid}/suivi");
        when(webPushGateway.send(anyString(), anyString(), anyString(), anyString())).thenReturn(201);

        service.sendToSubscription(USER_ID, SUBSCRIPTION_UUID, TITLE, BODY, ORDER_UUID);

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(webPushGateway).send(anyString(), anyString(), anyString(), payload.capture());
        assertThat(payload.getValue()).contains("\"url\":\"/mon-compte/commandes/" + ORDER_UUID + "/suivi\"");
    }

    @Test
    void sendIsNoOpWhenSubscriptionWasRemovedSinceFanOut() {
        when(subscriptionRepository.findByUuidAndUserEntity_KeycloakId(SUBSCRIPTION_UUID, USER_ID))
                .thenReturn(Optional.empty());

        service.sendToSubscription(USER_ID, SUBSCRIPTION_UUID, TITLE, BODY, ORDER_UUID);

        verify(properties, never()).isConfigured();
        verify(webPushGateway, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendDeletesExpiredSubscriptionWithoutCallingProvider() {
        final PushSubscriptionEntity expired = subscription(SUBSCRIPTION_UUID, Instant.now().minusSeconds(1));
        stubSubscription(expired);

        service.sendToSubscription(USER_ID, SUBSCRIPTION_UUID, TITLE, BODY, ORDER_UUID);

        verify(subscriptionRepository).delete(expired);
        verify(webPushGateway, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendDeletesSubscriptionReportedGoneByProvider() {
        final PushSubscriptionEntity subscription = subscription(SUBSCRIPTION_UUID, null);
        stubSubscription(subscription);
        when(properties.isConfigured()).thenReturn(true);
        when(webPushGateway.send(anyString(), anyString(), anyString(), anyString())).thenReturn(410);

        service.sendToSubscription(USER_ID, SUBSCRIPTION_UUID, TITLE, BODY, ORDER_UUID);

        verify(subscriptionRepository).delete(subscription);
    }

    @Test
    void sendRaisesRetryableFailureForProviderServerErrors() {
        stubSubscription(subscription(SUBSCRIPTION_UUID, null));
        when(properties.isConfigured()).thenReturn(true);
        when(webPushGateway.send(anyString(), anyString(), anyString(), anyString())).thenReturn(503);

        assertThatThrownBy(() -> service.sendToSubscription(USER_ID, SUBSCRIPTION_UUID, TITLE, BODY, ORDER_UUID))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("503");
        verify(subscriptionRepository, never()).delete(any(PushSubscriptionEntity.class));
    }

    @Test
    void sendFailsClearlyWhenVapidIsMissing() {
        stubSubscription(subscription(SUBSCRIPTION_UUID, null));
        when(properties.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.sendToSubscription(USER_ID, SUBSCRIPTION_UUID, TITLE, BODY, ORDER_UUID))
                .isInstanceOf(PushServiceNotConfiguredException.class);
    }

    private void stubSubscription(final PushSubscriptionEntity subscription) {
        when(subscriptionRepository.findByUuidAndUserEntity_KeycloakId(SUBSCRIPTION_UUID, USER_ID))
                .thenReturn(Optional.of(subscription));
    }

    private static PushSubscriptionEntity subscription(final UUID uuid, final Instant expirationTime) {
        return PushSubscriptionEntity.builder()
                .uuid(uuid)
                .endpointEncrypted("enc:" + ENDPOINT)
                .p256dhEncrypted("enc:p256dh")
                .authEncrypted("enc:auth")
                .expirationTime(expirationTime)
                .build();
    }
}
