package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.data.RecommendationSignalDto;
import com.novatech.cybertech.dto.request.event.UserEventBatchRequestDto;
import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.dto.response.userevent.UserEventBatchResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.UserEventTypeNotAcceptedException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.dto.UserEventDtoFixtures;
import com.novatech.cybertech.mappers.document.UserEventMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserEventRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.implementation.UserEventServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserEventServiceImpl}.
 *
 * Covers: existence guard, repo persistence (Mongo), enrichment defaults
 * (timestamp, sessionId, metadata, ingestedAt), per-type business validation
 * (productId required for VIEW/CLICK/etc, optional for SCROLL_DEPTH_HIGH/etc), the browser-only
 * restriction of consume-event, unknown products, the batch endpoint and the signals written by
 * the backend itself.
 */
@ExtendWith(MockitoExtension.class)
class UserEventServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserEventRepository userEventRepository;
    @Mock
    private UserEventMapper userEventMapper;
    @Mock
    private ProductRepository productRepository;

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
        when(productRepository.existsByUuid(UUID.fromString(dto.getProductId()))).thenReturn(true);
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

    @ParameterizedTest(name = "{0} is refused: only the backend produces it")
    @EnumSource(value = UserEventType.class, names = {"PURCHASE", "ADD_TO_CART", "REMOVE_FROM_CART", "WISHLIST_ADD",
            "RATING_POSITIVE", "RATING_NEGATIVE", "REVIEW_POSITIVE", "REVIEW_NEGATIVE"})
    void backendOnlyTypeIsRefused(final UserEventType type) {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEventBuilder().eventType(type).build();

        assertThatThrownBy(() -> service.processEvent(dto))
                .isInstanceOf(UserEventTypeNotAcceptedException.class)
                .extracting("field").isEqualTo("eventType");

        verifyNoInteractions(userRepository, userEventMapper, productRepository, userEventRepository);
    }

    @ParameterizedTest(name = "{0} is a browser signal")
    @EnumSource(value = UserEventType.class, names = {"VIEW", "CLICK", "COMPARE", "SEARCH_RESULT_CLICK", "SCROLL_DEPTH_HIGH",
            "HOVER", "SEARCH_QUERY", "IGNORE_RECOMMENDATION", "BOUNCE"})
    void browserSignalsAreAccepted(final UserEventType type) {
        assertThat(type.isBrowserSignal()).isTrue();
    }

    @Test
    @DisplayName("rejects: unknown productId -> ProductNotFoundException, nothing persisted")
    void unknownProductIsRejected() {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEvent();
        when(userRepository.existsByKeycloakId(dto.getUserId())).thenReturn(true);
        when(userEventMapper.toDocument(dto)).thenReturn(newDocFromDto(dto));
        when(productRepository.existsByUuid(UUID.fromString(dto.getProductId()))).thenReturn(false);

        assertThatThrownBy(() -> service.processEvent(dto)).isInstanceOf(ProductNotFoundException.class);

        verify(userEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects: productId that is not a UUID -> ProductNotFoundException without any query")
    void malformedProductIdIsRejected() {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEventBuilder().productId("not-a-uuid").build();
        when(userRepository.existsByKeycloakId(dto.getUserId())).thenReturn(true);
        when(userEventMapper.toDocument(dto)).thenReturn(newDocFromDto(dto));

        assertThatThrownBy(() -> service.processEvent(dto)).isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(productRepository);
        verify(userEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects: COMPARE without productId fails business validation (the compared product is the item)")
    void compareEventWithoutProductIdIsRejected() {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEventBuilder()
                .eventType(UserEventType.COMPARE)
                .productId(null)
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
        when(productRepository.existsByUuid(UUID.fromString(dto.getProductId()))).thenReturn(true);
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
        when(productRepository.existsByUuid(UUID.fromString(dto.getProductId()))).thenReturn(true);
        when(userEventRepository.save(mapped)).thenAnswer(inv -> inv.getArgument(0));

        UserEvent saved = service.processEvent(dto);

        assertThat(saved.getTimestamp()).isEqualTo(ts);
        assertThat(saved.getMetadata())
                .containsEntry("ingestedAt", "previously-set")  // putIfAbsent did NOT overwrite
                .containsEntry("source", "mobile");
    }

    @Nested
    @DisplayName("processEvents (batch)")
    class ProcessEvents {

        private static final String USER_ID = "kc-batch";

        private UserEventBatchRequestDto batchOf(final UserEventDto... events) {
            return UserEventBatchRequestDto.builder().events(new ArrayList<>(List.of(events))).build();
        }

        @Test
        @DisplayName("saves the events of known products, ignores unknown ones, forces the caller as userId")
        void savesKnownIgnoresUnknown() {
            final ProductEntity known = ProductEntityBuilder.aValidProductBuilder().build();
            final UserEventDto ok = UserEventDtoFixtures.aValidUserEventBuilder().userId(null).productId(known.getUuid().toString()).build();
            final UserEventDto unknown = UserEventDtoFixtures.aValidUserEventBuilder().userId("spoofed").build();
            final UserEventDto noProduct = UserEventDtoFixtures.aValidUserEventBuilder().eventType(UserEventType.SEARCH_QUERY).productId(null).build();
            when(userRepository.existsByKeycloakId(USER_ID)).thenReturn(true);
            when(userEventMapper.toDocument(any(UserEventDto.class))).thenAnswer(inv -> newDocFromDto(inv.getArgument(0)));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(known));

            final UserEventBatchResponseDto result = service.processEvents(batchOf(ok, unknown, noProduct), USER_ID);

            assertThat(result.getAccepted()).isEqualTo(2);
            assertThat(result.getIgnored()).isEqualTo(1);
            @SuppressWarnings("unchecked")
            final ArgumentCaptor<List<UserEvent>> captor = ArgumentCaptor.forClass(List.class);
            verify(userEventRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).extracting(UserEvent::getUserId).containsOnly(USER_ID);
            assertThat(captor.getValue()).extracting(UserEvent::getEventType).containsExactly(UserEventType.VIEW, UserEventType.SEARCH_QUERY);
        }

        @Test
        @DisplayName("a backend-only type anywhere refuses the whole batch, field events[i].eventType")
        void backendOnlyTypeRefusesBatch() {
            final UserEventDto ok = UserEventDtoFixtures.aValidUserEvent();
            final UserEventDto forged = UserEventDtoFixtures.aValidUserEventBuilder().eventType(UserEventType.PURCHASE).build();

            assertThatThrownBy(() -> service.processEvents(batchOf(ok, forged), USER_ID))
                    .isInstanceOf(UserEventTypeNotAcceptedException.class)
                    .extracting("field").isEqualTo("events[1].eventType");

            verifyNoInteractions(userEventRepository);
        }

        @Test
        @DisplayName("unknown caller -> UserNotFoundException")
        void unknownUser() {
            when(userRepository.existsByKeycloakId(USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.processEvents(batchOf(UserEventDtoFixtures.aValidUserEvent()), USER_ID))
                    .isInstanceOf(UserNotFoundException.class);
            verifyNoInteractions(userEventRepository);
        }

        @Test
        @DisplayName("only unknown products -> nothing saved, all ignored")
        void allUnknown() {
            when(userRepository.existsByKeycloakId(USER_ID)).thenReturn(true);
            when(userEventMapper.toDocument(any(UserEventDto.class))).thenAnswer(inv -> newDocFromDto(inv.getArgument(0)));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of());

            final UserEventBatchResponseDto result = service.processEvents(
                    batchOf(UserEventDtoFixtures.aValidUserEvent(), UserEventDtoFixtures.aValidUserEventBuilder().productId("bad").build()), USER_ID);

            assertThat(result.getAccepted()).isZero();
            assertThat(result.getIgnored()).isEqualTo(2);
            verify(userEventRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("recordServerSignals")
    class RecordServerSignals {

        private final UUID productUuid = UUID.randomUUID();
        private final UUID orderUuid = UUID.randomUUID();

        private RecommendationSignalDto signal(final UserEventType type, final UUID order) {
            return RecommendationSignalDto.builder().userId("kc-1").eventType(type).productUuid(productUuid)
                    .category("SMARTPHONE").brand("APPLE").price(new BigDecimal("999.00")).orderUuid(order).build();
        }

        @Test
        @DisplayName("writes a UserEvent with sessionId 'server', timestamp and category/brand/price/orderUuid metadata")
        void writesEvent() {
            when(userEventRepository.existsByEventTypeAndProductIdAndOrderUuid(UserEventType.PURCHASE, productUuid.toString(), orderUuid.toString())).thenReturn(false);

            service.recordServerSignals(List.of(signal(UserEventType.PURCHASE, orderUuid)));

            final ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
            verify(userEventRepository).save(captor.capture());
            final UserEvent saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo("kc-1");
            assertThat(saved.getSessionId()).isEqualTo("server");
            assertThat(saved.getEventType()).isEqualTo(UserEventType.PURCHASE);
            assertThat(saved.getProductId()).isEqualTo(productUuid.toString());
            assertThat(saved.getTimestamp()).isNotNull();
            assertThat(saved.getMetadata())
                    .containsEntry("category", "SMARTPHONE")
                    .containsEntry("brand", "APPLE")
                    .containsEntry("price", new BigDecimal("999.00"))
                    .containsEntry("orderUuid", orderUuid.toString());
        }

        @Test
        @DisplayName("PURCHASE already recorded for (order, product) -> skipped (complement payment)")
        void purchaseIsIdempotent() {
            when(userEventRepository.existsByEventTypeAndProductIdAndOrderUuid(UserEventType.PURCHASE, productUuid.toString(), orderUuid.toString())).thenReturn(true);

            service.recordServerSignals(List.of(signal(UserEventType.PURCHASE, orderUuid)));

            verify(userEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("non-PURCHASE signals are never checked for idempotence, no orderUuid in metadata")
        void otherTypesNotChecked() {
            service.recordServerSignals(List.of(signal(UserEventType.ADD_TO_CART, null)));

            verify(userEventRepository, never()).existsByEventTypeAndProductIdAndOrderUuid(any(), any(), any());
            final ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
            verify(userEventRepository).save(captor.capture());
            assertThat(captor.getValue().getMetadata()).doesNotContainKey("orderUuid");
        }

        @Test
        @DisplayName("MongoDB down -> no exception, the next signals are still attempted")
        void mongoFailureIsSwallowed() {
            when(userEventRepository.save(any())).thenThrow(new DataAccessResourceFailureException("mongo down"));

            assertThatCode(() -> service.recordServerSignals(List.of(signal(UserEventType.ADD_TO_CART, null), signal(UserEventType.WISHLIST_ADD, null))))
                    .doesNotThrowAnyException();

            verify(userEventRepository, times(2)).save(any());
        }
    }
}
