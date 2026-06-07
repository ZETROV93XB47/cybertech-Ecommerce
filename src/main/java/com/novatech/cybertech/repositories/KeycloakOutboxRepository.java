package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KeycloakOutboxRepository extends CrudBaseRepository<KeycloakOutboxEntity, Long> {

    /**
     * Rows still PENDING and untouched since {@code threshold} — i.e. left dangling by a crash.
     * A healthy synchronous request flips its row terminal within milliseconds, so anything
     * PENDING beyond the staleness window can only be a crash leftover.
     */
    List<KeycloakOutboxEntity> findByStatusAndUpdatedAtBefore(OutboxStatus status,
                                                              LocalDateTime threshold,
                                                              Pageable pageable);
}
