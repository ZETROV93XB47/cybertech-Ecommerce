package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.dto.request.notification.PushSubscriptionKeysDto;
import com.novatech.cybertech.dto.request.notification.PushSubscriptionRequestDto;
import com.novatech.cybertech.entities.PushSubscriptionEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.InvalidPushEndpointException;
import com.novatech.cybertech.exceptions.PushServiceNotConfiguredException;
import com.novatech.cybertech.exceptions.PushSubscriptionConflictException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    void deleteScopesTheDeleteToTheAuthenticatedUser() {
        service.delete(USER_ID, SUBSCRIPTION_UUID);

        verify(subscriptionRepository).deleteByUuidAndUserEntity_KeycloakId(SUBSCRIPTION_UUID, USER_ID);
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
