package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.UserEventType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface UserEventRepository extends MongoRepository<UserEvent, String> {

    List<UserEvent> findByUserId(String userId);

    List<UserEvent> findByTimestampBetween(Instant start, Instant end);

    List<UserEvent> findByEventType(UserEventType eventType);

    List<UserEvent> findByEventTypeAndUserId(UserEventType eventType, String userId);

    /** Idempotence of PURCHASE: one per (order, product), a complement payment does not repeat it. */
    @Query(value = "{ 'eventType': ?0, 'productId': ?1, 'metadata.orderUuid': ?2 }", exists = true)
    boolean existsByEventTypeAndProductIdAndOrderUuid(UserEventType eventType, String productId, String orderUuid);
}

