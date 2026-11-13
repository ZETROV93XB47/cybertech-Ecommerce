package com.novatech.cybertech.fixtures.support.stubs;

import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test configuration that swaps the real {@link Keycloak} admin client for a Mockito mock with the
 * standard {@code realm() -> users() -> create()} chain returning HTTP 201. Import in integration
 * tests that touch the user-registration flow but don't want a real Keycloak container.
 */
@TestConfiguration
public class KeycloakAdminStub {

    @Bean
    @Primary
    public Keycloak keycloak() {
        Keycloak keycloak = mock(Keycloak.class);
        RealmResource realm = mock(RealmResource.class);
        UsersResource users = mock(UsersResource.class);
        Response created = mock(Response.class);

        when(created.getStatus()).thenReturn(201);
        when(created.getLocation()).thenReturn(null);
        when(users.create(any())).thenReturn(created);
        when(realm.users()).thenReturn(users);
        when(keycloak.realm(any(String.class))).thenReturn(realm);

        return keycloak;
    }
}
