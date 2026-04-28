package com.novatech.cybertech.events.listener;

import com.novatech.cybertech.events.UserDeletedEvent;
import com.novatech.cybertech.services.implementation.KeycloakUserManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Mockito unit tests for {@link UserDeletionListener}.
 *
 * <p>Bug 4 fix: Keycloak deletion has been moved out of the transactional
 * {@code UserManagementServiceImp.deleteByUUID} flow into an
 * {@link org.springframework.transaction.event.TransactionPhase#AFTER_COMMIT} listener so the
 * external HTTP call only fires once the SQL delete is durable. We pin two contracts here:
 * <ul>
 *   <li>the listener delegates to {@link KeycloakUserManagementService#deleteUser} with the
 *       event's keycloakId,</li>
 *   <li>any failure from Keycloak is logged and swallowed — we are running on the publisher's
 *       commit thread post-domain-commit, propagating an exception would not undo the SQL
 *       delete and would only kill the worker, so the listener must not throw.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserDeletionListenerTest {

    @Mock KeycloakUserManagementService keycloakUserManagementService;

    @InjectMocks UserDeletionListener listener;

    @Test
    @DisplayName("onUserDeleted delegates to keycloakUserManagementService.deleteUser with the event's keycloakId")
    void onUserDeletedShouldCallKeycloakDelete() {
        UserDeletedEvent event = new UserDeletedEvent(this, "kc-deleted-123");

        listener.onUserDeleted(event);

        verify(keycloakUserManagementService).deleteUser("kc-deleted-123");
    }

    @Test
    @DisplayName("onUserDeleted swallows Keycloak failures (post-commit thread — operator must reconcile via logs)")
    void onUserDeletedShouldLogAndSwallowWhenKeycloakFails() {
        UserDeletedEvent event = new UserDeletedEvent(this, "kc-bad");
        doThrow(new RuntimeException("kc 500"))
                .when(keycloakUserManagementService).deleteUser("kc-bad");

        assertThatCode(() -> listener.onUserDeleted(event))
                .doesNotThrowAnyException();

        verify(keycloakUserManagementService).deleteUser("kc-bad");
    }
}
