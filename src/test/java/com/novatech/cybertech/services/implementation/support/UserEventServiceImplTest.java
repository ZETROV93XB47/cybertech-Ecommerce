package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.dto.UserEventDtoFixtures;
import com.novatech.cybertech.mappers.document.UserEventMapper;
import com.novatech.cybertech.repositories.UserEventRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.implementation.UserEventServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserEventServiceImpl}.
 *
 * Covers: existence guard, repo persistence (Mongo), enrichment defaults
 * (timestamp, sessionId, metadata, ingestedAt), and per-type business validation
 * (productId required for VIEW/CLICK/PURCHASE/etc, optional for SCROLL_DEPTH_HIGH/etc).
 */
@ExtendWith(MockitoExtension.class)
class UserEventServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserEventRepository userEventRepository;
    @Mock
    private UserEventMapper userEventMapper;

    @InjectMocks
    private UserEventServiceImpl service;

    private UserEvent newDocFromDto(final UserEventDto dto) {
        UserEvent event = UserEvent.builder()
                .userId(dto.getUserId())
                .eventType(dto.getEventType())
                .productId(dto.getProductId())
                .sessionId(dto.getSessionId())
                .metadata(dto.getMetadata())
                .build();
        // Mapper is supposed to ignore id + timestamp.
        return event;
    }

    @Test
    @DisplayName("happy: persists mapped UserEvent to the Mongo repository for an existing user")
    void processEventHappyPathPersistsDocument() {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEvent();
        UserEvent mapped = newDocFromDto(dto);

        when(userRepository.existsByKeycloakId(dto.getUserId())).thenReturn(true);
        when(userEventMapper.toDocument(dto)).thenReturn(mapped);
        when(userEventRepository.save(mapped)).thenAnswer(inv -> {
            UserEvent toSave = inv.getArgument(0);
            toSave.setId("mongo-id-1");
            return toSave;
        });

        UserEvent saved = service.processEvent(dto);

        assertThat(saved.getId()).isEqualTo("mongo-id-1");
        assertThat(saved.getUserId()).isEqualTo(dto.getUserId());

        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(userEventRepository).save(captor.capture());
        UserEvent persisted = captor.getValue();
        assertThat(persisted.getTimestamp()).isNotNull();
        assertThat(persisted.getSessionId()).isEqualTo(dto.getSessionId());
        assertThat(persisted.getMetadata()).containsKey("ingestedAt");
    }

    @Test
    @DisplayName("rejects: missing user keycloakId triggers UserNotFoundException; nothing is persisted")
    void unknownUserThrowsAndDoesNotPersist() {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEvent();
        when(userRepository.existsByKeycloakId(dto.getUserId())).thenReturn(false);

        assertThatThrownBy(() -> service.processEvent(dto))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(dto.getUserId());

        verify(userEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects: VIEW with blank productId fails business validation")
    void viewEventWithoutProductIdIsRejected() {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEventBuilder()
                .productId(" ")
                .build();
        UserEvent mapped = newDocFromDto(dto);
        when(userRepository.existsByKeycloakId(dto.getUserId())).thenReturn(true);
        when(userEventMapper.toDocument(dto)).thenReturn(mapped);

        assertThatThrownBy(() -> service.processEvent(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itemId is required");

        verify(userEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects: PURCHASE without productId fails business validation")
    void purchaseEventWithoutProductIdIsRejected() {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEventBuilder()
                .eventType(UserEventType.PURCHASE)
                .productId(null)
                .build();
        UserEvent mapped = newDocFromDto(dto);
        when(userRepository.existsByKeycloakId(dto.getUserId())).thenReturn(true);
        when(userEventMapper.toDocument(dto)).thenReturn(mapped);

        assertThatThrownBy(() -> service.processEvent(dto))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("accepts: SCROLL_DEPTH_HIGH does NOT require productId — passes business validation")
    void scrollDepthEventWithoutProductIsAllowed() {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEventBuilder()
                .eventType(UserEventType.SCROLL_DEPTH_HIGH)
                .productId(null)
                .build();
        UserEvent mapped = newDocFromDto(dto);
        when(userRepository.existsByKeycloakId(dto.getUserId())).thenReturn(true);
        when(userEventMapper.toDocument(dto)).thenReturn(mapped);
        when(userEventRepository.save(mapped)).thenReturn(mapped);

        UserEvent saved = service.processEvent(dto);

        assertThat(saved.getEventType()).isEqualTo(UserEventType.SCROLL_DEPTH_HIGH);
    }

    @Test
    @DisplayName("enrichment: missing sessionId is filled with a fresh UUID, metadata gains ingestedAt")
    void missingSessionIdIsAutoGeneratedAndMetadataIsSeeded() {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEventBuilder()
                .sessionId(null)
                .metadata(null)
                .build();
        UserEvent mapped = UserEvent.builder()
                .userId(dto.getUserId())
                .eventType(dto.getEventType())
                .productId(dto.getProductId())
                .sessionId(null)
                .metadata(null)
                .build();
        when(userRepository.existsByKeycloakId(dto.getUserId())).thenReturn(true);
        when(userEventMapper.toDocument(dto)).thenReturn(mapped);
        when(userEventRepository.save(mapped)).thenAnswer(inv -> inv.getArgument(0));

        UserEvent saved = service.processEvent(dto);

        assertThat(saved.getSessionId()).isNotNull();
        assertThat(saved.getMetadata()).isNotNull();
        assertThat(saved.getMetadata()).containsKey("ingestedAt");
        assertThat(Instant.parse((String) saved.getMetadata().get("ingestedAt"))).isNotNull();
    }

    @Test
    @DisplayName("enrichment: existing timestamp + metadata are preserved (putIfAbsent semantics)")
    void existingTimestampAndMetadataArePreserved() {
        Instant ts = Instant.parse("2025-01-02T03:04:05Z");
        Map<String, Object> meta = new HashMap<>();
        meta.put("ingestedAt", "previously-set");
        meta.put("source", "mobile");

        UserEventDto dto = UserEventDtoFixtures.aValidUserEvent();
        UserEvent mapped = UserEvent.builder()
                .userId(dto.getUserId())
                .eventType(dto.getEventType())
                .productId(dto.getProductId())
                .sessionId(dto.getSessionId())
                .metadata(meta)
                .timestamp(ts)
                .build();
        when(userRepository.existsByKeycloakId(dto.getUserId())).thenReturn(true);
        when(userEventMapper.toDocument(dto)).thenReturn(mapped);
        when(userEventRepository.save(mapped)).thenAnswer(inv -> inv.getArgument(0));

        UserEvent saved = service.processEvent(dto);

        assertThat(saved.getTimestamp()).isEqualTo(ts);
        assertThat(saved.getMetadata())
                .containsEntry("ingestedAt", "previously-set")  // putIfAbsent did NOT overwrite
                .containsEntry("source", "mobile");
    }
}
