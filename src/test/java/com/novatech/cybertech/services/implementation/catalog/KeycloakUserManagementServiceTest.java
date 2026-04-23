package com.novatech.cybertech.services.implementation.catalog;

import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.services.implementation.KeycloakUserManagementService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link KeycloakUserManagementService}.
 *
 * <p>SA-W3.5 wave — services/catalog. Pins BUG-085 (Keycloak admin client never closed).
 * Uses the Keycloak chain: {@code keycloak.realm(realm).users().create(...)} +
 * {@code .roles().get(role).toRepresentation()} + {@code .users().get(id).roles().realmLevel().add(...)}.
 */
@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class KeycloakUserManagementServiceTest {

    private static final String REALM = "cybertech";

    @Mock Keycloak keycloak;
    @Mock RealmResource realmResource;
    @Mock UsersResource usersResource;
    @Mock UserResource userResource;
    @Mock RolesResource rolesResource;
    @Mock RoleResource roleResource;
    @Mock RoleMappingResource roleMappingResource;
    @Mock RoleScopeResource roleScopeResource;

    private KeycloakUserManagementService service;

    @BeforeEach
    void setUp() {
        service = new KeycloakUserManagementService(keycloak);
        ReflectionTestUtils.setField(service, "realm", REALM);
    }

    private Response create201(String userId) {
        Response resp = org.mockito.Mockito.mock(Response.class);
        when(resp.getStatus()).thenReturn(201);
        when(resp.getLocation()).thenReturn(URI.create("https://keycloak/admin/realms/" + REALM + "/users/" + userId));
        return resp;
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("happy chain — creates user, parses Location, assigns realm role")
        void createUser_happyChain() {
            String userId = "abc-123";
            Response resp = create201(userId);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.create(any(UserRepresentation.class))).thenReturn(resp);
            when(realmResource.roles()).thenReturn(rolesResource);
            when(rolesResource.get("USER")).thenReturn(roleResource);
            RoleRepresentation roleRep = new RoleRepresentation();
            roleRep.setName("USER");
            when(roleResource.toRepresentation()).thenReturn(roleRep);
            when(usersResource.get(userId)).thenReturn(userResource);
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);

            String result = service.createUser("a@b.com", "John", "Smith", "S3cret!!", Role.USER);

            assertThat(result).isEqualTo(userId);
            ArgumentCaptor<List<RoleRepresentation>> rolesCaptor = ArgumentCaptor.forClass(List.class);
            verify(roleScopeResource).add(rolesCaptor.capture());
            assertThat(rolesCaptor.getValue()).extracting(RoleRepresentation::getName).containsExactly("USER");
        }

        @Test
        @DisplayName("non-201 response throws IllegalStateException with status + body")
        void createUser_4xx_throws() {
            Response resp = org.mockito.Mockito.mock(Response.class);
            when(resp.getStatus()).thenReturn(409);
            when(resp.readEntity(String.class)).thenReturn("user exists");
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.create(any(UserRepresentation.class))).thenReturn(resp);

            assertThatThrownBy(() -> service.createUser("a@b.com", "John", "Smith", "S3cret!!", Role.USER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("409")
                    .hasMessageContaining("user exists");

            // Role lookup must NOT happen on failure.
            verify(realmResource, never()).roles();
        }

        @Test
        @DisplayName("UserRepresentation carries email, names, enabled=true, emailVerified=true")
        void createUser_userRepresentationFields() {
            String userId = "u1";
            Response resp = create201(userId);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.create(any(UserRepresentation.class))).thenReturn(resp);
            when(realmResource.roles()).thenReturn(rolesResource);
            when(rolesResource.get("ADMIN")).thenReturn(roleResource);
            when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation());
            when(usersResource.get(userId)).thenReturn(userResource);
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);

            service.createUser("admin@x.com", "Jane", "Doe", "pw", Role.ADMIN);

            ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
            verify(usersResource).create(captor.capture());
            UserRepresentation rep = captor.getValue();
            assertThat(rep.getEmail()).isEqualTo("admin@x.com");
            assertThat(rep.getFirstName()).isEqualTo("Jane");
            assertThat(rep.getLastName()).isEqualTo("Doe");
            assertThat(rep.isEnabled()).isTrue();
            assertThat(rep.isEmailVerified()).isTrue();
            assertThat(rep.getUsername()).isEqualTo("jane.doe");
            assertThat(rep.getCredentials()).hasSize(1);
            assertThat(rep.getCredentials().get(0).getValue()).isEqualTo("pw");
            assertThat(rep.getCredentials().get(0).isTemporary()).isFalse();
        }

        @Test
        @DisplayName("blank firstName falls back to email-prefix as username")
        void createUser_blankFirstName_emailPrefixUsername() {
            String userId = "u2";
            Response resp = create201(userId);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.create(any(UserRepresentation.class))).thenReturn(resp);
            when(realmResource.roles()).thenReturn(rolesResource);
            when(rolesResource.get("USER")).thenReturn(roleResource);
            when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation());
            when(usersResource.get(userId)).thenReturn(userResource);
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);

            service.createUser("nick@example.org", "", "Doe", "pw", Role.USER);

            ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
            verify(usersResource).create(captor.capture());
            assertThat(captor.getValue().getUsername()).isEqualTo("nick");
        }

        @Test
        @DisplayName("BUG-085: createUser does NOT close the injected Keycloak admin client (leak risk pin)")
        void bug085_keycloakClientNeverClosed() {
            String userId = "abc-123";
            Response resp = create201(userId);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.create(any(UserRepresentation.class))).thenReturn(resp);
            when(realmResource.roles()).thenReturn(rolesResource);
            when(rolesResource.get("USER")).thenReturn(roleResource);
            when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation());
            when(usersResource.get(userId)).thenReturn(userResource);
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);

            service.createUser("a@b.com", "John", "Smith", "S3cret!!", Role.USER);

            verify(keycloak, never()).close();
        }

        @Test
        @DisplayName("Response from create() is closed (try-with-resources)")
        void createUser_responseIsClosed() {
            String userId = "abc-123";
            Response resp = create201(userId);
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.create(any(UserRepresentation.class))).thenReturn(resp);
            when(realmResource.roles()).thenReturn(rolesResource);
            when(rolesResource.get("USER")).thenReturn(roleResource);
            when(roleResource.toRepresentation()).thenReturn(new RoleRepresentation());
            when(usersResource.get(userId)).thenReturn(userResource);
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);

            service.createUser("a@b.com", "John", "Smith", "S3cret!!", Role.USER);

            verify(resp).close();
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("delegates to keycloak.realm(realm).users().delete(id)")
        void deleteUser_happyPath() {
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);

            service.deleteUser("abc-123");

            verify(usersResource).delete("abc-123");
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("partial update — only non-null DTO fields are propagated to UserRepresentation")
        void updateUser_partialUpdate_nonNullOnly() {
            UserUpdateRequestDto dto = new UserUpdateRequestDto();
            dto.setEmail("new@example.com"); // only email, names null

            UserRepresentation existing = new UserRepresentation();
            existing.setFirstName("OldFirst");
            existing.setLastName("OldLast");
            existing.setEmail("old@example.com");

            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.get("kc-1")).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(existing);

            service.updateUser("kc-1", dto);

            assertThat(existing.getFirstName()).isEqualTo("OldFirst");
            assertThat(existing.getLastName()).isEqualTo("OldLast");
            assertThat(existing.getEmail()).isEqualTo("new@example.com");
            verify(userResource).update(existing);
        }

        @Test
        @DisplayName("full update — all three fields propagated")
        void updateUser_allFields() {
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            dto.setFirstName("Alice");
            dto.setLastName("New");
            dto.setEmail("alice@x.com");

            UserRepresentation existing = new UserRepresentation();
            when(keycloak.realm(REALM)).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.get("kc-2")).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(existing);

            service.updateUser("kc-2", dto);

            assertThat(existing.getFirstName()).isEqualTo("Alice");
            assertThat(existing.getLastName()).isEqualTo("New");
            assertThat(existing.getEmail()).isEqualTo("alice@x.com");
            verify(userResource).update(existing);
        }
    }
}
