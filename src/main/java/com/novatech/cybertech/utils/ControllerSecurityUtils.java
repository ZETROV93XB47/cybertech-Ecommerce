package com.novatech.cybertech.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Centralises security-related helpers used across REST controllers (and a few service
 * impls that need to short-circuit role-aware logic without re-reading the JWT).
 *
 * <p>Rule of thumb: any helper that inspects an {@link Authentication} or the
 * {@link SecurityContextHolder} to derive an answer about the caller's identity or roles
 * belongs here, not in a controller. Controllers stay focused on request mapping, validation
 * and delegation to the service layer.</p>
 *
 * <p>Naming convention mirrors {@code KeycloakRoleConverter}: Spring authorities resolved
 * from Keycloak realm roles are prefixed with {@code ROLE_} and uppercased, hence
 * {@link #ROLE_ADMIN}.</p>
 */
public final class ControllerSecurityUtils {

    /**
     * Spring-Security authority string for the admin role, as produced by
     * {@code KeycloakRoleConverter} (i.e. {@code ROLE_ + uppercase realm-role name}).
     */
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private ControllerSecurityUtils() {
        // utility class — no instances
    }

    /**
     * Returns {@code true} when the supplied {@link Authentication} carries the
     * {@link #ROLE_ADMIN} authority. {@code null}-safe — returns {@code false} for an absent
     * or anonymous authentication.
     *
     * @param authentication the caller's {@link Authentication}, may be {@code null}.
     * @return {@code true} iff the authentication carries {@code ROLE_ADMIN}.
     */
    public static boolean isAdmin(final Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }

    /**
     * Returns {@code true} when the {@link SecurityContextHolder}'s current authentication
     * carries the {@link #ROLE_ADMIN} authority. Convenience overload for service-layer
     * callers that don't have an {@link Authentication} parameter on their signature.
     *
     * @return {@code true} iff the current SecurityContext authentication is an admin.
     */
    public static boolean isCurrentCallerAdmin() {
        return isAdmin(SecurityContextHolder.getContext().getAuthentication());
    }
}
