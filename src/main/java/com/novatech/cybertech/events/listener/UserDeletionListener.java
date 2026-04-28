package com.novatech.cybertech.events.listener;

import com.novatech.cybertech.events.UserDeletedEvent;
import com.novatech.cybertech.services.implementation.KeycloakUserManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drives the Keycloak side of the user-deletion flow.
 *
 * <p><b>WHY {@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT}:</b>
 * Keycloak's {@code deleteUser} is an external HTTP call to a separate identity store and
 * is irreversible. It MUST only happen once the local-DB delete has actually committed —
 * otherwise a rollback (e.g. an FK violation surfacing late) would leave Keycloak orphan-free
 * but the DB still holding a row that points at a now-deleted Keycloak subject.
 *
 * <p><b>Exception contract:</b> we are running after the domain commit on the publisher's
 * thread; throwing here would propagate up to the caller's flush boundary AFTER the SQL has
 * committed, which is useless to the API response. Failures are logged loudly so an operator
 * can reconcile manually, then swallowed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionListener {

    private final KeycloakUserManagementService keycloakUserManagementService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeleted(final UserDeletedEvent event) {
        try {
            keycloakUserManagementService.deleteUser(event.getKeycloakId());
        } catch (Exception e) {
            log.error("Failed to delete Keycloak user {} after DB delete committed — manual cleanup required",
                    event.getKeycloakId(), e);
        }
    }
}
