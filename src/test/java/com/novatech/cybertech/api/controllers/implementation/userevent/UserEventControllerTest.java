package com.novatech.cybertech.api.controllers.implementation.userevent;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.UserEventController;
import com.novatech.cybertech.api.error.ErrorManagementController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.fixtures.dto.UserEventDtoFixtures;
import com.novatech.cybertech.services.core.UserEventService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.LENIENT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for {@link UserEventController}.
 *
 * <p>Pinned bug status:
 * <ul>
 *   <li>BUG-035: F1.6 closed by replacing the {@code jakarta.mail.event.FolderEvent.CREATED}
 *       static import with {@code HttpStatus.CREATED}. Source verified — controller line 39 uses
 *       {@code HttpStatus.CREATED}. Asserted live by
 *       {@link #shouldCollectEventSuccessfullyReturning201Created}.</li>
 * </ul>
 */
@Import({TestSecurityConfig.class, ErrorManagementController.class})
@WebMvcTest(value = UserEventController.class)
class UserEventControllerTest {

    private static final String COLLECT_EVENT_ENDPOINT = "/api/v1/events/consume-event";

    private static final String USER_KEYCLOAK_ID = "keycloak-user-1";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserEventService userEventService;

    // ---------- POST /consume-event ----------

    @Test
    void shouldCollectEventSuccessfullyReturning201Created() throws Exception {
        // BUG-035 verification: controller now uses HttpStatus.CREATED (201), not the old
        // FolderEvent.CREATED int constant (1). A valid JWT-authenticated POST must return 201.
        final UserEventDto request = UserEventDtoFixtures.aValidUserEvent();
        final UserEvent persisted = UserEvent.builder()
                .id(UUID.randomUUID().toString())
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .productId(request.getProductId())
                .eventType(request.getEventType())
                .timestamp(Instant.now())
                .metadata(request.getMetadata())
                .build();

        when(userEventService.processEvent(any(UserEventDto.class))).thenReturn(persisted);

        mockMvc.perform(post(COLLECT_EVENT_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                // Compare on the stable shape of UserEvent. timestamp is set client-side here
                // so JSON shape is stable enough for a lenient compare on the load-bearing fields.
                .andExpect(jsonPath("$.id").value(persisted.getId()))
                .andExpect(jsonPath("$.userId").value(persisted.getUserId()))
                .andExpect(jsonPath("$.sessionId").value(persisted.getSessionId()))
                .andExpect(jsonPath("$.productId").value(persisted.getProductId()))
                .andExpect(jsonPath("$.eventType").value(persisted.getEventType().name()));
    }

    @Test
    void shouldForwardEventDtoUnchangedToService() throws Exception {
        // Confirm the @RequestBody DTO is forwarded as-is (subject is captured separately).
        final UserEventDto request = UserEventDtoFixtures.aValidUserEventBuilder()
                .eventType(UserEventType.CLICK)
                .build();

        when(userEventService.processEvent(any(UserEventDto.class)))
                .thenReturn(UserEvent.builder().id(UUID.randomUUID().toString()).build());

        mockMvc.perform(post(COLLECT_EVENT_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated());

        final ArgumentCaptor<UserEventDto> captor = ArgumentCaptor.forClass(UserEventDto.class);
        verify(userEventService).processEvent(captor.capture());

        final UserEventDto forwarded = captor.getValue();
        assertEquals(request.getUserId(), forwarded.getUserId());
        assertEquals(request.getSessionId(), forwarded.getSessionId());
        assertEquals(request.getProductId(), forwarded.getProductId());
        assertEquals(UserEventType.CLICK, forwarded.getEventType());
    }

    @Test
    void shouldFailCollectEventCauseDtoBadRequestReturning400() throws Exception {
        // Empty DTO violates @NotBlank/@NotNull on userId, eventType, productId, sessionId.
        final UserEventDto invalid = new UserEventDto();

        mockMvc.perform(post(COLLECT_EVENT_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
    }

    @Test
    void shouldRejectCollectEventWhenAnonymousReturning401() throws Exception {
        // /api/v1/events/** is NOT in PUBLIC_URLS → anonymous → 401.
        final UserEventDto request = UserEventDtoFixtures.aValidUserEvent();

        mockMvc.perform(post(COLLECT_EVENT_ENDPOINT)
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCollectEventAsAdminWithoutUserRoleReturning403() throws Exception {
        // @PreAuthorize("hasRole('USER')") on the method — an ADMIN-only token (no ROLE_USER)
        // must yield 403 (BUG-031 wiring + W0's @EnableMethodSecurity).
        final UserEventDto request = UserEventDtoFixtures.aValidUserEvent();
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        mockMvc.perform(post(COLLECT_EVENT_ENDPOINT)
                        .with(jwtAdmin("keycloak-admin"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(error), LENIENT));
    }
}
