package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.dto.request.notification.PushSubscriptionKeysDto;
import com.novatech.cybertech.dto.request.notification.PushSubscriptionRequestDto;
import com.novatech.cybertech.entities.PushSubscriptionEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.InvalidPushEndpointException;
import com.novatech.cybertech.exceptions.PushServiceNotConfiguredException;
import com.novatech.cybertech.exceptions.PushSubscriptionConflictException;
import com.novatech.cybertech.exceptions.PushSubscriptionNotFoundException;
import com.novatech.cybertech.exceptions.PushSubscriptionNotOwnedException;
import com.novatech.cybertech.repositories.PushSubscriptionRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.SensitiveDataEncryptionService;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceImpTest {

    private static final String USER_ID = "kc-user";
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/token";
    private static final String PUBLIC_KEY = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[65]);
    private static final String AUTH_SECRET = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    private static final UUID SUBSCRIPTION_UUID = UUID.randomUUID();
    private static final int MAX_SUBSCRIPTIONS = 5;

    @Mock
    private PushSubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SensitiveDataEncryptionService encryptionService;
    @Mock
    private WebPushProperties properties;

    @InjectMocks
    private PushSubscriptionServiceImp service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = UserEntityBuilder.aValidUserBuilder().keycloakId(USER_ID).build();
        lenient().when(properties.isConfigured()).thenReturn(true);
        lenient().when(properties.getAllowedEndpointHosts()).thenReturn(List.of("fcm.googleapis.com"));
        lenient().when(properties.getMaxSubscriptionsPerUser()).thenReturn(MAX_SUBSCRIPTIONS);
    }

    @Test
    void registerEncryptsEndpointAndBrowserKeysAndAssociatesJwtUser() {
        when(userRepository.findByKeycloakIdAndIsActive(USER_ID, true)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByEndpointHash(any())).thenReturn(Optional.empty());
        when(encryptionService.encrypt(any())).thenAnswer(invocation -> "encrypted:" + invocation.getArgument(0));
        final PushSubscriptionEntity saved = PushSubscriptionEntity.builder().uuid(SUBSCRIPTION_UUID).build();
        when(subscriptionRepository.save(any(PushSubscriptionEntity.class))).thenReturn(saved);

        final var response = service.register(USER_ID, validRequest());

        assertThat(response.uuid()).isEqualTo(SUBSCRIPTION_UUID);
        final ArgumentCaptor<PushSubscriptionEntity> captor = ArgumentCaptor.forClass(PushSubscriptionEntity.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserEntity()).isSameAs(user);
        assertThat(captor.getValue().getEndpointEncrypted()).isEqualTo("encrypted:" + ENDPOINT);
        assertThat(captor.getValue().getP256dhEncrypted()).isEqualTo("encrypted:" + PUBLIC_KEY);
        assertThat(captor.getValue().getAuthEncrypted()).isEqualTo("encrypted:" + AUTH_SECRET);
    }

    @Test
    void registerUpdatesExistingEndpointOwnedBySameUser() {
        final PushSubscriptionEntity existing = PushSubscriptionEntity.builder().userEntity(user).build();
        when(userRepository.findByKeycloakIdAndIsActive(USER_ID, true)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByEndpointHash(any())).thenReturn(Optional.of(existing));
        when(encryptionService.encrypt(any())).thenAnswer(invocation -> "encrypted:" + invocation.getArgument(0));
        when(subscriptionRepository.save(existing)).thenReturn(existing);

        service.register(USER_ID, validRequest());

        verify(subscriptionRepository).save(existing);
    }

    @Test
    void registerRejectsEndpointAlreadyOwnedByAnotherUser() {
        final UserEntity otherUser = UserEntityBuilder.aValidUserBuilder().keycloakId("other-user").build();
        final PushSubscriptionEntity existing = PushSubscriptionEntity.builder().userEntity(otherUser).build();
        when(userRepository.findByKeycloakIdAndIsActive(USER_ID, true)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByEndpointHash(any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(USER_ID, validRequest()))
                .isInstanceOf(PushSubscriptionConflictException.class);
    }

    @Test
    void registerRejectsEndpointOutsideTrustedPushProviders() {
        final PushSubscriptionRequestDto request = validRequest();
        request.setEndpoint("https://127.0.0.1/internal");

        assertThatThrownBy(() -> service.register(USER_ID, request))
                .isInstanceOf(InvalidPushEndpointException.class);
    }

    @Test
    void registerRejectsInvalidBrowserKeyLength() {
        final PushSubscriptionRequestDto request = validRequest();
        request.getKeys().setAuth(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[8]));
        assertThatThrownBy(() -> service.register(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subscription keys");
    }

    @Test
    void registerRejectsWhenVapidIsNotConfigured() {
        when(properties.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.register(USER_ID, validRequest()))
                .isInstanceOf(PushServiceNotConfiguredException.class);
    }

    @Test
    void registerNewBrowserBelowLimitEvictsNothing() {
        stubNewRegistration();
        when(subscriptionRepository.findAllByUserEntity_KeycloakIdOrderByCreatedAtAsc(USER_ID))
                .thenReturn(existingSubscriptions(MAX_SUBSCRIPTIONS - 1));

        service.register(USER_ID, validRequest());

        verify(subscriptionRepository, never()).deleteAll(any());
    }

    @Test
    void registerSixthBrowserReplacesTheOldestSubscription() {
        stubNewRegistration();
        final List<PushSubscriptionEntity> existing = existingSubscriptions(MAX_SUBSCRIPTIONS);
        when(subscriptionRepository.findAllByUserEntity_KeycloakIdOrderByCreatedAtAsc(USER_ID)).thenReturn(existing);

        service.register(USER_ID, validRequest());

        verify(subscriptionRepository).deleteAll(List.of(existing.getFirst()));
    }

    @Test
    void registerAboveLimitAfterLoweringItKeepsOnlyTheNewestBrowsers() {
        stubNewRegistration();
        final List<PushSubscriptionEntity> existing = existingSubscriptions(MAX_SUBSCRIPTIONS + 2);
        when(subscriptionRepository.findAllByUserEntity_KeycloakIdOrderByCreatedAtAsc(USER_ID)).thenReturn(existing);

        service.register(USER_ID, validRequest());

        // 7 existing + 1 new must end at 5: the 3 oldest go.
        verify(subscriptionRepository).deleteAll(existing.subList(0, 3));
    }

    @Test
    void registerSameBrowserAgainNeverEvicts() {
        final PushSubscriptionEntity existing = PushSubscriptionEntity.builder().id(1L).userEntity(user).build();
        when(userRepository.findByKeycloakIdAndIsActive(USER_ID, true)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByEndpointHash(any())).thenReturn(Optional.of(existing));
        when(encryptionService.encrypt(any())).thenAnswer(invocation -> "encrypted:" + invocation.getArgument(0));
        when(subscriptionRepository.save(existing)).thenReturn(existing);

        service.register(USER_ID, validRequest());

        verify(subscriptionRepository, never()).findAllByUserEntity_KeycloakIdOrderByCreatedAtAsc(any());
        verify(subscriptionRepository, never()).deleteAll(any());
    }

    @Test
    void deleteRemovesSubscriptionOwnedByCaller() {
        final PushSubscriptionEntity subscription = PushSubscriptionEntity.builder()
                .uuid(SUBSCRIPTION_UUID).userEntity(user).build();
        when(subscriptionRepository.findByUuid(SUBSCRIPTION_UUID)).thenReturn(Optional.of(subscription));

        service.delete(USER_ID, SUBSCRIPTION_UUID);

        verify(subscriptionRepository).delete(subscription);
    }

    @Test
    void deleteRejectsSubscriptionOfAnotherUserWithoutDeleting() {
        final UserEntity otherUser = UserEntityBuilder.aValidUserBuilder().keycloakId("other-user").build();
        when(subscriptionRepository.findByUuid(SUBSCRIPTION_UUID)).thenReturn(Optional.of(
                PushSubscriptionEntity.builder().uuid(SUBSCRIPTION_UUID).userEntity(otherUser).build()));

        assertThatThrownBy(() -> service.delete(USER_ID, SUBSCRIPTION_UUID))
                .isInstanceOf(PushSubscriptionNotOwnedException.class);
        verify(subscriptionRepository, never()).delete(any(PushSubscriptionEntity.class));
    }

    @Test
    void deleteUnknownSubscriptionIsNotFound() {
        when(subscriptionRepository.findByUuid(SUBSCRIPTION_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, SUBSCRIPTION_UUID))
                .isInstanceOf(PushSubscriptionNotFoundException.class);
        verify(subscriptionRepository, never()).delete(any(PushSubscriptionEntity.class));
    }

    private void stubNewRegistration() {
        when(userRepository.findByKeycloakIdAndIsActive(USER_ID, true)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByEndpointHash(any())).thenReturn(Optional.empty());
        when(encryptionService.encrypt(any())).thenAnswer(invocation -> "encrypted:" + invocation.getArgument(0));
        when(subscriptionRepository.save(any(PushSubscriptionEntity.class)))
                .thenReturn(PushSubscriptionEntity.builder().uuid(SUBSCRIPTION_UUID).build());
    }

    private List<PushSubscriptionEntity> existingSubscriptions(final int count) {
        return IntStream.range(0, count)
                .<PushSubscriptionEntity>mapToObj(index -> PushSubscriptionEntity.builder()
                        .id((long) index + 1)
                        .uuid(UUID.randomUUID())
                        .userEntity(user)
                        .build())
                .toList();
    }

    private static PushSubscriptionRequestDto validRequest() {
        final PushSubscriptionKeysDto keys = new PushSubscriptionKeysDto();
        keys.setP256dh(PUBLIC_KEY);
        keys.setAuth(AUTH_SECRET);
        final PushSubscriptionRequestDto request = new PushSubscriptionRequestDto();
        request.setEndpoint(ENDPOINT);
        request.setKeys(keys);
        return request;
    }
}
