package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.PushNotificationControllerApiSpec;
import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.dto.request.notification.PushSubscriptionRequestDto;
import com.novatech.cybertech.dto.response.notification.PushPublicKeyResponseDto;
import com.novatech.cybertech.dto.response.notification.PushSubscriptionResponseDto;
import com.novatech.cybertech.exceptions.PushServiceNotConfiguredException;
import com.novatech.cybertech.services.core.PushSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.PUSH_NOTIFICATION_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = PUSH_NOTIFICATION_CONTROLLER_BASE_PATH)
public class PushNotificationController implements PushNotificationControllerApiSpec {

    private static final String PUBLIC_KEY_PATH = "/public-key";
    private static final String SUBSCRIPTIONS_PATH = "/subscriptions";

    private final WebPushProperties webPushProperties;
    private final PushSubscriptionService pushSubscriptionService;

    @Override
    @GetMapping(value = PUBLIC_KEY_PATH, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<PushPublicKeyResponseDto> getPublicKey() {
        if (!webPushProperties.isConfigured()) {
            throw new PushServiceNotConfiguredException();
        }
        return ResponseEntity.ok(PushPublicKeyResponseDto.builder()
                .publicKey(webPushProperties.getPublicKey())
                .build());
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    @PostMapping(value = SUBSCRIPTIONS_PATH, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<PushSubscriptionResponseDto> register(
            @Valid @RequestBody final PushSubscriptionRequestDto request,
            @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pushSubscriptionService.register(jwt.getSubject(), request));
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping(SUBSCRIPTIONS_PATH + "/{subscriptionUuid}")
    public ResponseEntity<Void> delete(
            @PathVariable final UUID subscriptionUuid,
            @AuthenticationPrincipal final Jwt jwt) {
        pushSubscriptionService.delete(jwt.getSubject(), subscriptionUuid);
        return ResponseEntity.noContent().build();
    }
}
