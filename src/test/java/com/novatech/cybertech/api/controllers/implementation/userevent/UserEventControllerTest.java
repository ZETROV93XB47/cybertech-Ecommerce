package com.novatech.cybertech.api.controllers.implementation.userevent;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.UserEventController;
import com.novatech.cybertech.api.error.ErrorManagementController;
import com.novatech.cybertech.api.error.enumpackage.ErrorCode;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.constants.CyberTechAppConstants;
import com.novatech.cybertech.dto.request.event.UserEventBatchRequestDto;
import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.dto.response.userevent.UserEventBatchResponseDto;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.UserEventTypeNotAcceptedException;
import com.novatech.cybertech.dto.response.userevent.UserEventResponseDto;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.fixtures.dto.UserEventDtoFixtures;
import com.novatech.cybertech.mappers.document.UserEventMapper;
import com.novatech.cybertech.services.core.UserEventService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
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
 *   <li>Replaced the {@code jakarta.mail.event.FolderEvent.CREATED}
 *       static import with {@code HttpStatus.CREATED}. Source verified — controller line 39 uses
 *       {@code HttpStatus.CREATED}. Asserted live by
 *       {@link #shouldCollectEventSuccessfullyReturning201Created}.</li>
 * </ul>
 */
@Import({TestSecurityConfig.class, ErrorManagementController.class})
@WebMvcTest(value = UserEventController.class)
class UserEventControllerTest {

    private static final String COLLECT_EVENT_ENDPOINT = "/api/v1/events/consume-event";
    private static final String COLLECT_EVENTS_ENDPOINT = "/api/v1/events/consume-events";

    private static final String USER_KEYCLOAK_ID = "keycloak-user-1";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserEventService userEventService;

    @MockitoBean
    UserEventMapper userEventMapper;

    // ---------- POST /consume-event ----------

    @Test
    void shouldCollectEventSuccessfullyReturning201Created() throws Exception {
        // Controller now uses HttpStatus.CREATED (201), not the old
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

        final UserEventResponseDto responseDto = UserEventResponseDto.builder()
                .id(persisted.getId())
                .userId(persisted.getUserId())
                .sessionId(persisted.getSessionId())
                .productId(persisted.getProductId())
                .eventType(persisted.getEventType())
                .timestamp(persisted.getTimestamp())
                .metadata(persisted.getMetadata())
                .build();

        when(userEventService.processEvent(any(UserEventDto.class))).thenReturn(persisted);
        when(userEventMapper.toResponseDto(persisted)).thenReturn(responseDto);

        mockMvc.perform(post(COLLECT_EVENT_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                // Compare on the stable shape of UserEventResponseDto. timestamp is set client-side here
                // so JSON shape is stable enough for a lenient compare on the load-bearing fields.
                .andExpect(jsonPath("$.id").value(responseDto.getId()))
                .andExpect(jsonPath("$.userId").value(responseDto.getUserId()))
                .andExpect(jsonPath("$.sessionId").value(responseDto.getSessionId()))
                .andExpect(jsonPath("$.productId").value(responseDto.getProductId()))
                .andExpect(jsonPath("$.eventType").value(responseDto.getEventType().name()));
    }

    @Test
    void shouldDeserializeCompareEventFromRawJson() throws Exception {
        final String body = """
                {"userId": "ignored", "sessionId": "session-1", "productId": "product-compared", "eventType": "COMPARE"}
                """;
        final UserEvent persisted = UserEvent.builder().id("evt-1").userId(USER_KEYCLOAK_ID).sessionId("session-1")
                .productId("product-compared").eventType(UserEventType.COMPARE).timestamp(Instant.now()).build();
        when(userEventService.processEvent(any(UserEventDto.class))).thenReturn(persisted);
        when(userEventMapper.toResponseDto(persisted)).thenReturn(UserEventResponseDto.builder()
                .id("evt-1").eventType(UserEventType.COMPARE).productId("product-compared").build());

        mockMvc.perform(post(COLLECT_EVENT_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("COMPARE"));

        final org.mockito.ArgumentCaptor<UserEventDto> captor = org.mockito.ArgumentCaptor.forClass(UserEventDto.class);
        org.mockito.Mockito.verify(userEventService).processEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(UserEventType.COMPARE);
        assertThat(captor.getValue().getProductId()).isEqualTo("product-compared");
    }

    @Test
    void shouldOverwriteUserIdFromJwtBeforeForwardingToService() throws Exception {
        // The controller derives userId from the JWT subject (regardless of
        // whatever the body contained) before delegating to the service. Verify both that the
        // overwrite happens and that the rest of the payload is forwarded as-is.
        final UserEventDto request = UserEventDtoFixtures.aValidUserEventBuilder()
                .eventType(UserEventType.CLICK)
                .userId("some-spoofed-other-user-id")
                .build();

        when(userEventService.processEvent(any(UserEventDto.class)))
                .thenReturn(UserEvent.builder().id(UUID.randomUUID().toString()).build());
        when(userEventMapper.toResponseDto(any(UserEvent.class)))
                .thenReturn(UserEventResponseDto.builder().id(UUID.randomUUID().toString()).build());

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
        // userId must come from the JWT subject, not from the body.
        assertEquals(USER_KEYCLOAK_ID, forwarded.getUserId());
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
        // must yield 403 (access-denied wiring + @EnableMethodSecurity).
        final UserEventDto request = UserEventDtoFixtures.aValidUserEvent();
        final ErrorResponseDto error = ErrorResponseDto.builder()
                .message("Access denied")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .errorCode(ErrorCode.ACCESS_DENIED)
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

    // ---------- browser-only types, unknown product ----------

    @Test
    void shouldRefuseBackendOnlyEventTypeReturning400WithFieldError() throws Exception {
        final UserEventDto request = UserEventDtoFixtures.aValidUserEventBuilder().eventType(UserEventType.PURCHASE).build();
        when(userEventService.processEvent(any(UserEventDto.class)))
                .thenThrow(new UserEventTypeNotAcceptedException("eventType", "eventType PURCHASE is produced by the backend and cannot be sent by a client"));

        mockMvc.perform(post(COLLECT_EVENT_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("eventType"))
                .andExpect(jsonPath("$.fieldErrors[0].message", startsWith("eventType PURCHASE")));
    }

    @Test
    void shouldReturn404WhenProductIsUnknown() throws Exception {
        final UserEventDto request = UserEventDtoFixtures.aValidUserEvent();
        when(userEventService.processEvent(any(UserEventDto.class)))
                .thenThrow(new ProductNotFoundException("No product with the UUID : " + request.getProductId() + " found"));

        mockMvc.perform(post(COLLECT_EVENT_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    // ---------- POST /consume-events ----------

    @Test
    void shouldCollectEventsBatchReturning201WithCountsForTheJwtUser() throws Exception {
        final UserEventBatchRequestDto request = UserEventBatchRequestDto.builder()
                .events(List.of(UserEventDtoFixtures.aValidUserEvent(), UserEventDtoFixtures.aValidUserEventBuilder().eventType(UserEventType.COMPARE).build()))
                .build();
        when(userEventService.processEvents(any(UserEventBatchRequestDto.class), eq(USER_KEYCLOAK_ID)))
                .thenReturn(UserEventBatchResponseDto.builder().accepted(1).ignored(1).build());

        mockMvc.perform(post(COLLECT_EVENTS_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"accepted\":1,\"ignored\":1}", JsonCompareMode.STRICT));

        verify(userEventService).processEvents(any(UserEventBatchRequestDto.class), eq(USER_KEYCLOAK_ID));
    }

    @Test
    void shouldRejectEmptyBatchReturning400() throws Exception {
        mockMvc.perform(post(COLLECT_EVENTS_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content("{\"events\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("events"));
        verifyNoInteractions(userEventService);
    }

    @Test
    void shouldRejectBatchOverTheLimitReturning400() throws Exception {
        final List<UserEventDto> events = IntStream.range(0, CyberTechAppConstants.USER_EVENT_BATCH_MAX_SIZE + 1)
                .mapToObj(i -> UserEventDtoFixtures.aValidUserEvent())
                .toList();

        mockMvc.perform(post(COLLECT_EVENTS_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(UserEventBatchRequestDto.builder().events(events).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("events"));
        verifyNoInteractions(userEventService);
    }

    @Test
    void shouldValidateEachEventOfTheBatchReturning400() throws Exception {
        mockMvc.perform(post(COLLECT_EVENTS_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content("{\"events\":[{\"productId\":\"p\",\"sessionId\":\"s\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("events[0].eventType"));
        verifyNoInteractions(userEventService);
    }

    @Test
    void shouldReportTheBatchIndexOfAForgedTypeReturning400() throws Exception {
        when(userEventService.processEvents(any(UserEventBatchRequestDto.class), eq(USER_KEYCLOAK_ID)))
                .thenThrow(new UserEventTypeNotAcceptedException("events[1].eventType", "eventType ADD_TO_CART is produced by the backend and cannot be sent by a client"));

        mockMvc.perform(post(COLLECT_EVENTS_ENDPOINT)
                        .with(jwtUser(USER_KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(UserEventBatchRequestDto.builder().events(List.of(
                                UserEventDtoFixtures.aValidUserEvent(),
                                UserEventDtoFixtures.aValidUserEventBuilder().eventType(UserEventType.ADD_TO_CART).build())).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("events[1].eventType"));
    }

    @Test
    void shouldRejectBatchWhenAnonymousReturning401() throws Exception {
        mockMvc.perform(post(COLLECT_EVENTS_ENDPOINT)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(UserEventBatchRequestDto.builder().events(List.of(UserEventDtoFixtures.aValidUserEvent())).build())))
                .andExpect(status().isUnauthorized());
    }
}
