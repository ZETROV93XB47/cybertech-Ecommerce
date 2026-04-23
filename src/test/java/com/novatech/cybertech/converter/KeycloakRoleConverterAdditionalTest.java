package com.novatech.cybertech.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Companion to {@link com.novatech.cybertech.security.KeycloakRoleConverterTest}.
 *
 * <p>Covers the branches that the original test left untested:
 * <ul>
 *   <li>{@code realm_access} present but no {@code roles} key → {@code getOrDefault} fallback;</li>
 *   <li>role names with mixed case → must be uppercased in {@code ROLE_…};</li>
 *   <li>role names with hyphens / underscores → preserved verbatim after the {@code ROLE_} prefix;</li>
 *   <li>large role list → preserves order in returned authorities collection.</li>
 * </ul>
 */
class KeycloakRoleConverterAdditionalTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    @Nested
    @DisplayName("realm_access edge cases")
    class RealmAccessEdgeCases {

        @Test
        @DisplayName("realm_access present without 'roles' key -> empty authorities (getOrDefault fallback)")
        void shouldReturnEmptyWhenRolesKeyMissing() {
            // Given – realm_access is a non-null map but lacks a 'roles' entry
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("not_roles", List.of("admin"));

            Jwt jwt = Jwt.withTokenValue("fake-token")
                    .header("alg", "none")
                    .claim("realm_access", realmAccess)
                    .build();

            // When
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            // Then
            assertThat(authorities).isEmpty();
        }
    }

    @Nested
    @DisplayName("role-name normalisation")
    class RoleNormalisation {

        @Test
        @DisplayName("Lower-case role names should be upper-cased in ROLE_ authorities")
        void shouldUpperCaseLowerCaseRoles() {
            // Given
            Jwt jwt = Jwt.withTokenValue("fake-token")
                    .header("alg", "none")
                    .claim("realm_access", Map.of("roles", List.of("user", "admin", "manager")))
                    .build();

            // When
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            // Then
            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN", "ROLE_MANAGER");
        }

        @Test
        @DisplayName("Mixed-case role names should be normalised to upper-case")
        void shouldNormaliseMixedCase() {
            // Given
            Jwt jwt = Jwt.withTokenValue("fake-token")
                    .header("alg", "none")
                    .claim("realm_access", Map.of("roles", List.of("AdMiN", "uSeR")))
                    .build();

            // When
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            // Then
            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        }

        @Test
        @DisplayName("Role names with hyphens / underscores are preserved verbatim after ROLE_")
        void shouldPreserveSpecialChars() {
            // Given
            Jwt jwt = Jwt.withTokenValue("fake-token")
                    .header("alg", "none")
                    .claim("realm_access", Map.of("roles", List.of("super-admin", "system_user")))
                    .build();

            // When
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            // Then
            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_SUPER-ADMIN", "ROLE_SYSTEM_USER");
        }

        @Test
        @DisplayName("Large role list preserves input order in the returned collection")
        void shouldPreserveOrder() {
            // Given – 6 roles, distinct
            List<String> input = List.of("a", "b", "c", "d", "e", "f");
            Jwt jwt = Jwt.withTokenValue("fake-token")
                    .header("alg", "none")
                    .claim("realm_access", Map.of("roles", input))
                    .build();

            // When
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            // Then – converter returns a List (Collectors.toList()) so order matters
            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_A", "ROLE_B", "ROLE_C", "ROLE_D", "ROLE_E", "ROLE_F");
        }
    }
}
