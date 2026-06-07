package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.entities.enums.Role;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakUserManagementService {

    @Value("${keycloak.client.user.management.realm}")
    private String realm;
    private final Keycloak keycloakClient;


    public String createUser(String email, String firstName, String lastName, String rawPassword, Role role) {

        final UsersResource users = keycloakClient.realm(realm).users();

        UserRepresentation userRepresentation = getUserRepresentation(email, firstName, lastName, rawPassword);

        final String locationHeaderValue;
        try (Response resp = users.create(userRepresentation)) {

            if (resp.getStatus() != 201) {
                String msg = resp.readEntity(String.class);
                throw new IllegalStateException("Keycloak user creation failed: " + resp.getStatus() + " " + msg);
            }

            // L’ID est dans le Location header: .../users/{id}
            locationHeaderValue = resp.getLocation().toString();

            String userId = getUserKeycloakId(locationHeaderValue);

            log.info("userID : {}", userId);

            RoleRepresentation userRole = keycloakClient.realm(realm)
                    .roles()
                    .get(role.name())
                    .toRepresentation();

            log.info("userRole : {}", userRole);

            users.get(userId).roles().realmLevel().add(List.of(userRole));

        }
        return getUserKeycloakId(locationHeaderValue);
    }

    private static String getUserKeycloakId(String locationHeaderValue) {
        return locationHeaderValue.substring(locationHeaderValue.lastIndexOf('/') + 1);
    }

    private UserRepresentation getUserRepresentation(String email, String firstName, String lastName, String rawPassword) {
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(createUserNameFromFirstNameAndLastName(firstName, lastName, email));
        userRepresentation.setEmail(email);
        userRepresentation.setFirstName(firstName);
        userRepresentation.setLastName(lastName);
        userRepresentation.setEnabled(true);
        userRepresentation.setEmailVerified(true);

        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setTemporary(false);
        cred.setValue(rawPassword);
        userRepresentation.setCredentials(List.of(cred));
        return userRepresentation;
    }

    /**
     * Idempotent (404-tolerant) delete. The outbox reconciliation job may re-issue a delete for a
     * user already removed by the synchronous path — a 404 from Keycloak therefore counts as
     * success. Any other non-2xx status is surfaced (it would previously be silently swallowed
     * because the returned {@link Response} was never inspected).
     */
    public void deleteUser(String keycloakUserId) {
        try (Response resp = keycloakClient.realm(realm).users().delete(keycloakUserId)) {
            final int status = resp.getStatus();
            if (status == Response.Status.NOT_FOUND.getStatusCode()) {
                log.info("Keycloak user {} already absent on delete — treating as done (idempotent)", keycloakUserId);
            } else if (status >= 400) {
                throw new IllegalStateException("Keycloak user delete failed: " + status);
            }
        }
    }

    /**
     * Reconciliation lookup: resolve a Keycloak user id by exact email, or empty if none.
     * Used by the outbox job to detect a crash-orphan (Keycloak user with no DB row).
     */
    public Optional<String> searchByEmail(final String email) {
        return keycloakClient.realm(realm).users().searchByEmail(email, true).stream()
                .findFirst()
                .map(UserRepresentation::getId);
    }

    private String createUserNameFromFirstNameAndLastName(final String firstName, final String lastName, final String email) {
        if ((!StringUtils.isEmpty(firstName)) && (!StringUtils.isEmpty(lastName))) {
            return firstName.trim().toLowerCase() + "." + lastName.trim().toLowerCase();
        }
        return email.split("@")[0];
    }

    public void updateUser(String keycloakId, UserUpdateRequestDto userUpdateRequestDto) {
        UserResource userResource = keycloakClient.realm(realm).users().get(keycloakId);
        UserRepresentation userRep = userResource.toRepresentation();

        if (userUpdateRequestDto.getFirstName() != null) userRep.setFirstName(userUpdateRequestDto.getFirstName());
        if (userUpdateRequestDto.getLastName() != null) userRep.setLastName(userUpdateRequestDto.getLastName());
        if (userUpdateRequestDto.getEmail() != null) userRep.setEmail(userUpdateRequestDto.getEmail());

        userResource.update(userRep);
    }
}
