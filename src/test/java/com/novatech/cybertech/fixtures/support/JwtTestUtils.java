package com.novatech.cybertech.fixtures.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Static helpers that produce {@link JwtRequestPostProcessor}s with the right Keycloak-derived
 * authority set. Use with {@code mockMvc.perform(...).with(jwtUser(...))}.
 */
public final class JwtTestUtils {

    private JwtTestUtils() {
    }

    public static JwtRequestPostProcessor jwtUser(final String keycloakId) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(jwt -> jwt.subject(keycloakId));
    }

    public static JwtRequestPostProcessor jwtAdmin(final String keycloakId) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(jwt -> jwt.subject(keycloakId));
    }

    /**
     * No-op processor representing an anonymous request. Returned as a {@link RequestPostProcessor}
     * so call sites that already use {@code .with(...)} can stay consistent.
     */
    public static RequestPostProcessor jwtAnonymous() {
        return request -> request;
    }
}
