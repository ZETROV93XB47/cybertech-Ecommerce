package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.document.UserEventMapper;
import com.novatech.cybertech.repositories.UserEventRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.UserEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventServiceImpl implements UserEventService {

    private final UserRepository userRepository; // Pour vérifier l'existence du user
    private final UserEventRepository repository;
    private final UserEventMapper userEventMapper;

    @Override
    public UserEvent processEvent(UserEventDto eventDto) {
        log.info("Processing event for user: {}", eventDto.getUserId());

        // On suppose que userId est le keycloakId ou l'UUID. Adaptez selon votre logique.
        if (!userExists(eventDto.getUserId())) {
            throw new UserNotFoundException("User not found: " + eventDto.getUserId());
        }

        UserEvent event = userEventMapper.toDocument(eventDto);

        // Validation métier spécifique (ex: itemId requis pour certains types)
        validateBusinessRules(event);

        enrich(event);

        UserEvent savedEvent = repository.save(event);
        log.info("Event saved with ID: {}", savedEvent.getId());
        return savedEvent;
    }

    private void validateBusinessRules(UserEvent event) {
        // Certains events nécessitent un itemId
        if (requiresItem(event.getEventType()) && (event.getProductId() == null || event.getProductId().isBlank())) {
            throw new IllegalArgumentException("itemId is required for eventType " + event.getEventType());
        }
    }

    private boolean requiresItem(UserEventType type) {
        return switch (type) {
            case VIEW, CLICK, ADD_TO_CART, PURCHASE, WISHLIST_ADD,
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