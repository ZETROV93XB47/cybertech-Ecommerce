package com.novatech.cybertech.services.implementation;

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
import com.novatech.cybertech.mappers.document.UserEventMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserEventRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.UserEventService;
import com.novatech.cybertech.utils.LogSafetyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_EVENT_METADATA_BRAND;
import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_EVENT_METADATA_CATEGORY;
import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_EVENT_METADATA_ORDER_UUID;
import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_EVENT_METADATA_PRICE;
import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_EVENT_SERVER_SESSION_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventServiceImpl implements UserEventService {

    private static final String EVENT_TYPE_FIELD = "eventType";
    private static final String BATCH_EVENT_TYPE_FIELD = "events[%d].eventType";
    private static final String EVENT_TYPE_NOT_ACCEPTED_MESSAGE = "eventType %s is produced by the backend and cannot be sent by a client";
    private static final String USER_NOT_FOUND_MESSAGE = "User not found: ";

    private final UserRepository userRepository; // Pour vérifier l'existence du user
    private final UserEventRepository repository;
    private final UserEventMapper userEventMapper;
    private final ProductRepository productRepository;


    @Override
    public UserEvent processEvent(UserEventDto eventDto) {
        log.info("Processing event for user: {}", eventDto.getUserId());

        assertBrowserSignal(eventDto.getEventType(), EVENT_TYPE_FIELD);

        // On suppose que userId est le keycloakId ou l'UUID. Adaptez selon votre logique.
        if (!userExists(eventDto.getUserId())) {
            throw new UserNotFoundException(USER_NOT_FOUND_MESSAGE + eventDto.getUserId());
        }

        UserEvent event = userEventMapper.toDocument(eventDto);

        // Validation métier spécifique (ex: itemId requis pour certains types)
        validateBusinessRules(event);

        // An event on an unknown product would only become an orphan feedback in Gorse.
        if (hasProduct(event) && !productExists(event.getProductId())) {
            throw new ProductNotFoundException("No product with the UUID : " + event.getProductId() + " found");
        }

        enrich(event);

        UserEvent savedEvent = repository.save(event);
        log.info("Event saved with ID: {}", savedEvent.getId());
        return savedEvent;
    }

    @Override
    public UserEventBatchResponseDto processEvents(final UserEventBatchRequestDto batch, final String userId) {
        final List<UserEventDto> events = batch.getEvents();
        // All-or-nothing on the types: a batch carrying a backend-only type is a forged request.
        IntStream.range(0, events.size())
                .forEach(i -> assertBrowserSignal(events.get(i).getEventType(), BATCH_EVENT_TYPE_FIELD.formatted(i)));

        if (!userExists(userId)) {
            throw new UserNotFoundException(USER_NOT_FOUND_MESSAGE + userId);
        }

        final List<UserEvent> documents = events.stream()
                .map(dto -> {
                    dto.setUserId(userId);
                    return userEventMapper.toDocument(dto);
                })
                .toList();
        documents.forEach(this::validateBusinessRules);

        // Unlike the single endpoint, an unknown product does not fail the batch: one stale card on
        // the page would otherwise lose every other signal of the page.
        final Set<String> knownProducts = knownProductIds(documents);
        final List<UserEvent> accepted = documents.stream()
                .filter(event -> !hasProduct(event) || knownProducts.contains(event.getProductId()))
                .toList();
        accepted.forEach(this::enrich);
        if (!accepted.isEmpty()) {
            repository.saveAll(accepted);
        }

        final int ignored = documents.size() - accepted.size();
        log.info("Batch of {} event(s) for user {}: {} saved, {} ignored (unknown product)",
                documents.size(), LogSafetyUtils.maskUuid(userId), accepted.size(), ignored);
        return UserEventBatchResponseDto.builder().accepted(accepted.size()).ignored(ignored).build();
    }

    @Override
    public void recordServerSignals(final List<RecommendationSignalDto> signals) {
        signals.forEach(this::recordServerSignal);
    }

    private void recordServerSignal(final RecommendationSignalDto signal) {
        final String productId = signal.getProductUuid().toString();
        try {
            if (signal.getEventType() == UserEventType.PURCHASE && signal.getOrderUuid() != null
                    && repository.existsByEventTypeAndProductIdAndOrderUuid(UserEventType.PURCHASE, productId, signal.getOrderUuid().toString())) {
                log.info("PURCHASE of product {} for order {} already recorded — skipped", productId, signal.getOrderUuid());
                return;
            }
            repository.save(UserEvent.builder()
                    .userId(signal.getUserId())
                    .sessionId(USER_EVENT_SERVER_SESSION_ID)
                    .eventType(signal.getEventType())
                    .productId(productId)
                    .timestamp(Instant.now())
                    .metadata(metadataOf(signal))
                    .build());
            log.debug("Recommendation signal {} stored for product {}", signal.getEventType(), productId);
        } catch (RuntimeException e) {
            // MongoDB down or slow: the business action is already committed, the signal is lost.
            log.warn("Recommendation signal not stored: type={} productUuid={} userId={} cause={}",
                    signal.getEventType(), productId, LogSafetyUtils.maskUuid(signal.getUserId()), e.toString());
        }
    }

    private static Map<String, Object> metadataOf(final RecommendationSignalDto signal) {
        final Map<String, Object> metadata = new HashMap<>();
        Optional.ofNullable(signal.getCategory()).ifPresent(v -> metadata.put(USER_EVENT_METADATA_CATEGORY, v));
        Optional.ofNullable(signal.getBrand()).ifPresent(v -> metadata.put(USER_EVENT_METADATA_BRAND, v));
        Optional.ofNullable(signal.getPrice()).ifPresent(v -> metadata.put(USER_EVENT_METADATA_PRICE, v));
        Optional.ofNullable(signal.getOrderUuid()).ifPresent(v -> metadata.put(USER_EVENT_METADATA_ORDER_UUID, v.toString()));
        return metadata;
    }

    private static void assertBrowserSignal(final UserEventType type, final String field) {
        if (type != null && !type.isBrowserSignal()) {
            throw new UserEventTypeNotAcceptedException(field, EVENT_TYPE_NOT_ACCEPTED_MESSAGE.formatted(type));
        }
    }

    private static boolean hasProduct(final UserEvent event) {
        return event.getProductId() != null && !event.getProductId().isBlank();
    }

    private boolean productExists(final String productId) {
        return parseUuid(productId).map(productRepository::existsByUuid).orElse(false);
    }

    private Set<String> knownProductIds(final List<UserEvent> events) {
        final Set<UUID> uuids = events.stream()
                .filter(UserEventServiceImpl::hasProduct)
                .map(event -> parseUuid(event.getProductId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (uuids.isEmpty()) {
            return Set.of();
        }
        return productRepository.findAllByUuidIn(uuids).stream()
                .map(ProductEntity::getUuid)
                .map(UUID::toString)
                .collect(Collectors.toSet());
    }

    private static Optional<UUID> parseUuid(final String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void validateBusinessRules(UserEvent event) {
        // Certains events nécessitent un itemId
        if (requiresItem(event.getEventType()) && !hasProduct(event)) {
            throw new IllegalArgumentException("itemId is required for eventType " + event.getEventType());
        }
    }

    private boolean requiresItem(UserEventType type) {
        return switch (type) {
            case VIEW, CLICK, ADD_TO_CART, PURCHASE, WISHLIST_ADD, COMPARE,
                 REMOVE_FROM_CART, SEARCH_RESULT_CLICK,
                 RATING_POSITIVE, RATING_NEGATIVE,
                 REVIEW_POSITIVE, REVIEW_NEGATIVE -> true;
            default -> false;
        };
    }

    private void enrich(UserEvent event) {
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        if (event.getSessionId() == null || event.getSessionId().isBlank()) {
            event.setSessionId(UUID.randomUUID().toString());
        }
        if (event.getMetadata() == null) {
            event.setMetadata(new HashMap<>());
        }
        event.getMetadata().putIfAbsent("ingestedAt", Instant.now().toString());
    }

    @Cacheable(cacheNames = "userExistence", key = "#userId")
    public boolean userExists(String userId) {
        return userRepository.existsByKeycloakId(userId);
    }

}
