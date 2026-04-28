package com.novatech.cybertech.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Fired by {@link com.novatech.cybertech.services.implementation.UserManagementServiceImp#deleteByUUID}
 * once the local-DB delete has completed. Consumed by
 * {@link com.novatech.cybertech.events.listener.UserDeletionListener} under
 * {@link org.springframework.transaction.event.TransactionPhase#AFTER_COMMIT} so the
 * non-transactional Keycloak {@code deleteUser} call only fires when the SQL portion has
 * actually committed.
 *
 * <p>Carries only the Keycloak subject id — the DB row no longer exists by the time the
 * listener runs, so we cannot resolve {@code keycloakId} from the entity at consumption time.
 */
@Getter
public class UserDeletedEvent extends ApplicationEvent {

    private final String keycloakId;

    public UserDeletedEvent(final Object source, final String keycloakId) {
        super(source);
        this.keycloakId = keycloakId;
    }
}
