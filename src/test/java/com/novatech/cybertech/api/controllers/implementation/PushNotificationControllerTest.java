package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.exceptions.PushSubscriptionNotOwnedException;
import com.novatech.cybertech.exceptions.PushSubscriptionNotFoundException;
import static org.mockito.Mockito.doThrow;
import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.error.ErrorManagementController;
import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.dto.request.notification.PushSubscriptionRequestDto;
import com.novatech.cybertech.dto.response.notification.PushSubscriptionResponseDto;
import com.novatech.cybertech.exceptions.InvalidPushEndpointException;
import com.novatech.cybertech.services.core.PushSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestSecurityConfig.class, ErrorManagementController.class})
@WebMvcTest(PushNotificationController.class)
class PushNotificationControllerTest {

    private static final String BASE_PATH = "/api/v1/services/notifications/push";
    private static final String USER_ID = "keycloak-user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebPushProperties properties;

    @MockitoBean
    private PushSubscriptionService subscriptionService;

    @Test
    void publicKeyEndpointIsAccessibleWithoutAuthentication() throws Exception {
        when(properties.isConfigured()).thenReturn(true);
        when(properties.getPublicKey()).thenReturn("public-vapid-key");

        mockMvc.perform(get(BASE_PATH + "/public-key").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("public-vapid-key"));
    }

    @Test
    void subscriptionRegistrationUsesJwtSubjectAndReturnsCreated() throws Exception {
        final UUID uuid = UUID.randomUUID();
        when(subscriptionService.register(eq(USER_ID), any(PushSubscriptionRequestDto.class)))
                .thenReturn(PushSubscriptionResponseDto.builder().uuid(uuid).build());

        mockMvc.perform(post(BASE_PATH + "/subscriptions")
                        .with(jwtUser(USER_ID))
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content("""
                                {
                                  "endpoint": "https://fcm.googleapis.com/fcm/send/token",
                                  "keys": {
                                    "p256dh": "key",
                                    "auth": "secret"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value(uuid.toString()));

        verify(subscriptionService).register(eq(USER_ID), any(PushSubscriptionRequestDto.class));
    }

    @Test
    void anonymousCannotRegisterSubscription() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/subscriptions")
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(new PushSubscriptionRequestDto())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUsesJwtSubjectToScopeSubscription() throws Exception {
        final UUID uuid = UUID.randomUUID();

        mockMvc.perform(delete(BASE_PATH + "/subscriptions/" + uuid).with(jwtUser(USER_ID)))
                .andExpect(status().isNoContent());

        verify(subscriptionService).delete(USER_ID, uuid);
    }

    @Test
    void invalidEndpointReturnsBadRequest() throws Exception {
        when(subscriptionService.register(eq(USER_ID), any(PushSubscriptionRequestDto.class)))
                .thenThrow(new InvalidPushEndpointException());

        mockMvc.perform(post(BASE_PATH + "/subscriptions")
                        .with(jwtUser(USER_ID))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "endpoint": "https://example.invalid/push",
                                  "keys": {"p256dh":"key","auth":"secret"}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSubscriptionOfAnotherUserIsForbidden() throws Exception {
        final UUID uuid = UUID.randomUUID();
        doThrow(new PushSubscriptionNotOwnedException()).when(subscriptionService).delete(USER_ID, uuid);

        mockMvc.perform(delete(BASE_PATH + "/subscriptions/" + uuid).with(jwtUser(USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PUSH_SUBSCRIPTION_NOT_OWNED"));
    }

    @Test
    void deleteUnknownSubscriptionIsNotFound() throws Exception {
        final UUID uuid = UUID.randomUUID();
        doThrow(new PushSubscriptionNotFoundException()).when(subscriptionService).delete(USER_ID, uuid);

        mockMvc.perform(delete(BASE_PATH + "/subscriptions/" + uuid).with(jwtUser(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PUSH_SUBSCRIPTION_NOT_FOUND"));
    }
}
