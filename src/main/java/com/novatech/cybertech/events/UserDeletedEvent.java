package com.novatech.cybertech.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Fired by {@link com.novatech.cybertech.services.implementation.UserManagementServiceImp#deleteByUUID}
 * (and the bulk variant) AFTER the local-DB delete + the CO-COMMITTED DELETE outbox row.
 * Consumed by {@link com.novatech.cybertech.events.listener.UserDeletionListener} under
 * {@link org.springframework.transaction.event.TransactionPhase#AFTER_COMMIT}, which reconciles
 * that outbox row immediately (Keycloak delete + mark DONE).
 *
 * <p>The event only carries the outbox row's uuid — durability no longer depends on this
 * in-memory event at all: if the listener fails or the process crashes, the row stays PENDING
 * and the reconciliation batch job is the durable backstop.
 */
@Getter
public class UserDeletedEvent extends ApplicationEvent {

    private final UUID outboxUuid;

    public UserDeletedEvent(final Object source, final UUID outboxUuid) {
        super(source);
        this.outboxUuid = outboxUuid;
    }
}
