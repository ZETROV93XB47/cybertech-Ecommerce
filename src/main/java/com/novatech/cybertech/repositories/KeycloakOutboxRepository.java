package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KeycloakOutboxRepository extends CrudBaseRepository<KeycloakOutboxEntity, Long> {

    /**
     * Rows still PENDING and untouched since {@code threshold} — i.e. left dangling by a crash.
     * A healthy synchronous request flips its row terminal within milliseconds, so anything
     * PENDING beyond the staleness window can only be a crash leftover.
     *
     * <p>Ordered oldest-first so a single reconciliation batch applies same-user UPDATE rows in
     * creation order — see {@code KeycloakOutboxServiceImp#reconcileUpdate}'s supersede check for
     * why the relative order of same-user rows matters when their payloads touch the same field.
     */
    List<KeycloakOutboxEntity> findByStatusAndUpdatedAtBeforeOrderByCreatedAtAsc(OutboxStatus status,
                                                              LocalDateTime threshold,
                                                              Pageable pageable);

    /**
     * {@code true} when a later UPDATE row for the same user has already been fully applied
     * ({@link OutboxStatus#DONE}). Used by the supersede guard in
     * {@code KeycloakOutboxServiceImp#reconcileUpdate} — a {@code PENDING}/{@code FAILED} newer
     * row hasn't actually written anything yet, so it's not grounds to skip re-applying an older
     * one; only a {@code DONE} row is proof that fresher data already landed.
     */
    boolean existsByKeycloakIdAndOperationTypeAndStatusAndCreatedAtAfter(@Param("keycloakId") String keycloakId,
                                                                          @Param("operationType") OutboxOperationType operationType,
                                                                          @Param("status") OutboxStatus status,
                                                                          @Param("createdAt") LocalDateTime createdAt);
}
