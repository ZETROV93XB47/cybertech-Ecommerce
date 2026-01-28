package com.novatech.cybertech.security;

import com.novatech.cybertech.converter.KeycloakRoleConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    @Test
    @DisplayName("Devrait convertir les rôles Keycloak en GrantedAuthorities avec le préfixe ROLE_")
    void shouldConvertRolesWhenPresent() {
        // Given
        Jwt jwt = Jwt.withTokenValue("fake-token")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("user", "admin")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        // When
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Then
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Devrait retourner une liste vide si la claim realm_access est absente")
    void shouldReturnEmptyListWhenRealmAccessIsMissing() {
        // Given
        Jwt jwt = Jwt.withTokenValue("fake-token")
                .header("alg", "none")
                .claim("some_other_claim", "value") // realm_access manquant
                .build();

        // When
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Then
        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("Devrait retourner une liste vide si la liste des rôles est vide")
    void shouldReturnEmptyListWhenRolesAreEmpty() {
        // Given
        Jwt jwt = Jwt.withTokenValue("fake-token")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of()))
                .build();

        // When
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // Then
        assertThat(authorities).isEmpty();
    }
}