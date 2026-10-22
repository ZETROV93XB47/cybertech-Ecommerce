package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.notification.PushSubscriptionRequestDto;
import com.novatech.cybertech.dto.response.notification.PushPublicKeyResponseDto;
import com.novatech.cybertech.dto.response.notification.PushSubscriptionResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

@Tag(name = "Push notifications", description = "Browser push subscription management")
public interface PushNotificationControllerApiSpec {

    @Operation(summary = "Get the public VAPID key")
    @ApiResponse(responseCode = "200", description = "Public VAPID key")
    @ApiResponse(responseCode = "503", description = "Web Push is not configured",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    ResponseEntity<PushPublicKeyResponseDto> getPublicKey();

    @Operation(summary = "Register or update this browser's push subscription",
            description = "The authenticated JWT subject owns the subscription; any user identifier in the body is ignored/not accepted. "
                    + "A user keeps at most 5 browsers (cybertech.web-push.max-subscriptions-per-user): registering one more replaces the oldest.")
    @ApiResponse(responseCode = "201", description = "Subscription registered",
            content = @Content(schema = @Schema(implementation = PushSubscriptionResponseDto.class)))
    ResponseEntity<PushSubscriptionResponseDto> register(PushSubscriptionRequestDto request, Jwt jwt);

    @Operation(summary = "Remove one of the authenticated user's push subscriptions")
    @ApiResponse(responseCode = "204", description = "Subscription removed")
    @ApiResponse(responseCode = "403", description = "Subscription belongs to another user",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Subscription not found (already removed or replaced by a newer browser)",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    ResponseEntity<Void> delete(UUID subscriptionUuid, Jwt jwt);
}
