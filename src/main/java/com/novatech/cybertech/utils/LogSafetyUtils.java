package com.novatech.cybertech.utils;

import java.util.UUID;

/**
 * Centralised PII masking helpers for log statements.
 *
 * <p>FIX(PII-LEAK): A code audit revealed that {@code log.info("... {}", req)} on user-related DTOs
 * was dumping raw email, password, bank-card numbers, JWT tokens and other regulated fields into
 * application logs. This class concentrates the masking primitives so that:
 * <ul>
 *     <li>Services can log "something happened to a user" without exposing the actual identity.</li>
 *     <li>The masking strategy stays consistent across the codebase — no per-class re-implementation.</li>
 *     <li>Future audit changes (GDPR / PCI / Keycloak migration) only touch one file.</li>
 * </ul>
 *
 * <p>All helpers are null-safe and never throw — a logging path must never be the root cause of a
 * failed request, so any malformed input is masked into a sentinel ({@code "<null>"} /
 * {@code "<invalid>"}) instead of bubbling up.
 */
public final class LogSafetyUtils {

    private static final String NULL_SENTINEL = "<null>";
    private static final String INVALID_SENTINEL = "<invalid>";
    private static final String MASKED_LOCAL_PART = "***";
    private static final String UUID_TAIL_MASK = "***";
    /** UUIDs are 36 chars (with dashes); we keep the first 6 to make logs greppable without leaking the whole id. */
    private static final int UUID_PREFIX_KEEP = 6;

    private LogSafetyUtils() {
        // utility class — no instantiation
    }

    /**
     * Returns a log-safe representation of an email address that hides the local part but keeps
     * the domain so operators can still spot delivery / spam / corporate-domain patterns.
     *
     * <p>Examples: {@code "alice@acme.com"} -> {@code "***@acme.com"}; {@code null} -> {@code "<null>"};
     * {@code "no-at-sign"} -> {@code "<invalid>"}.
     *
     * @param email raw email address (may be {@code null} or malformed).
     * @return masked form safe to send to log appenders.
     */
    public static String maskEmail(final String email) {
        if (email == null) {
            return NULL_SENTINEL;
        }
        final int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return INVALID_SENTINEL;
        }
        return MASKED_LOCAL_PART + "@" + email.substring(at + 1);
    }

    /**
     * Returns the domain portion of an email (everything after the {@code '@'}) — useful when a
     * service only needs to attribute traffic to a tenant / corporate domain without correlating
     * back to a single user.
     *
     * <p>Examples: {@code "alice@acme.com"} -> {@code "acme.com"}; {@code null} -> {@code "<null>"};
     * {@code "no-at-sign"} -> {@code "<invalid>"}.
     */
    public static String extractEmailDomain(final String email) {
        if (email == null) {
            return NULL_SENTINEL;
        }
        final int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return INVALID_SENTINEL;
        }
        return email.substring(at + 1);
    }

    /**
     * Masks a UUID by keeping the first {@value #UUID_PREFIX_KEEP} characters and replacing the
     * tail with {@value #UUID_TAIL_MASK}. The prefix preserves enough entropy to cross-reference
     * a request id across log lines while still preventing direct lookup of the underlying entity.
     *
     * <p>Example: {@code 550e8400-e29b-41d4-a716-446655440000} -> {@code 550e84***}.
     */
    public static String maskUuid(final UUID uuid) {
        if (uuid == null) {
            return NULL_SENTINEL;
        }
        final String full = uuid.toString();
        if (full.length() <= UUID_PREFIX_KEEP) {
            return full + UUID_TAIL_MASK;
        }
        return full.substring(0, UUID_PREFIX_KEEP) + UUID_TAIL_MASK;
    }
}
