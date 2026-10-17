package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.UserEventType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface UserEventRepository extends MongoRepository<UserEvent, String> {

    List<UserEvent> findByUserId(String userId);

    List<UserEvent> findByTimestampBetween(Instant start, Instant end);

    List<UserEvent> findByEventType(UserEventType eventType);

    List<UserEvent> findByEventTypeAndUserId(UserEventType eventType, String userId);
}

