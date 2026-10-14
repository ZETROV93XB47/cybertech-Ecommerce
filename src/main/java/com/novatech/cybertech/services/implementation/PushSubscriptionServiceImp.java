package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.dto.request.notification.PushSubscriptionRequestDto;
import com.novatech.cybertech.dto.response.notification.PushSubscriptionResponseDto;
import com.novatech.cybertech.entities.PushSubscriptionEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.InvalidPushEndpointException;
import com.novatech.cybertech.exceptions.PushServiceNotConfiguredException;
import com.novatech.cybertech.exceptions.PushSubscriptionConflictException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.repositories.PushSubscriptionRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.PushSubscriptionService;
import com.novatech.cybertech.services.core.SensitiveDataEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PushSubscriptionServiceImp implements PushSubscriptionService {

    private static final String SHA_256_ALGORITHM = "SHA-256";
    private static final String HTTPS_SCHEME = "https";
    private static final int HTTPS_PORT = 443;
    private static final int P256DH_KEY_LENGTH_BYTES = 65;
    private static final int AUTH_SECRET_LENGTH_BYTES = 16;

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;
    private final SensitiveDataEncryptionService encryptionService;
    private final WebPushProperties webPushProperties;

    @Override
    @Transactional
    public PushSubscriptionResponseDto register(final String keycloakId, final PushSubscriptionRequestDto request) {
        if (!webPushProperties.isConfigured()) {
            throw new PushServiceNotConfiguredException();
        }

        validateEndpoint(request.getEndpoint());
        validateSubscriptionKeys(request);
        final String endpointHash = hashEndpoint(request.getEndpoint());
        final UserEntity user = userRepository.findByKeycloakIdAndIsActive(keycloakId, true)
                .orElseThrow(() -> new UserNotFoundException("Active user not found"));

        final PushSubscriptionEntity subscription = pushSubscriptionRepository.findByEndpointHash(endpointHash)
                .map(existing -> {
                    if (!existing.getUserEntity().getKeycloakId().equals(keycloakId)) {
                        throw new PushSubscriptionConflictException();
                    }
                    return existing;
                })
                .orElseGet(PushSubscriptionEntity::new);

        subscription.setEndpointHash(endpointHash);
        subscription.setEndpointEncrypted(encryptionService.encrypt(request.getEndpoint()));
        subscription.setP256dhEncrypted(encryptionService.encrypt(request.getKeys().getP256dh()));
        subscription.setAuthEncrypted(encryptionService.encrypt(request.getKeys().getAuth()));
        subscription.setExpirationTime(request.getExpirationTime() == null
                ? null
                : Instant.ofEpochMilli(request.getExpirationTime()));
        subscription.setUserEntity(user);

        final PushSubscriptionEntity saved = pushSubscriptionRepository.save(subscription);
        return PushSubscriptionResponseDto.builder().uuid(saved.getUuid()).build();
    }

    @Override
    @Transactional
    public void delete(final String keycloakId, final UUID subscriptionUuid) {
        pushSubscriptionRepository.deleteByUuidAndUserEntity_KeycloakId(subscriptionUuid, keycloakId);
    }

    private URI validateEndpoint(final String rawEndpoint) {
        try {
            final URI endpoint = URI.create(rawEndpoint);
            final String host = endpoint.getHost();
            final boolean allowedHost = host != null && webPushProperties.getAllowedEndpointHosts().stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(suffix -> host.toLowerCase(Locale.ROOT).equals(suffix)
                            || host.toLowerCase(Locale.ROOT).endsWith("." + suffix));
            if (!HTTPS_SCHEME.equalsIgnoreCase(endpoint.getScheme())
                    || endpoint.getUserInfo() != null
                    || (endpoint.getPort() != -1 && endpoint.getPort() != HTTPS_PORT)
                    || !allowedHost) {
                throw new InvalidPushEndpointException();
            }

            return endpoint;
        } catch (IllegalArgumentException exception) {
            throw new InvalidPushEndpointException();
        }
    }

    private static void validateSubscriptionKeys(final PushSubscriptionRequestDto request) {
        try {
            if (Base64.getUrlDecoder().decode(request.getKeys().getP256dh()).length != P256DH_KEY_LENGTH_BYTES
                    || Base64.getUrlDecoder().decode(request.getKeys().getAuth()).length != AUTH_SECRET_LENGTH_BYTES) {
                throw new IllegalArgumentException("Invalid Web Push key lengths");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Web Push subscription keys", exception);
        }
    }

    private static String hashEndpoint(final String endpoint) {
        try {
            final byte[] digest = MessageDigest.getInstance(SHA_256_ALGORITHM)
                    .digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
