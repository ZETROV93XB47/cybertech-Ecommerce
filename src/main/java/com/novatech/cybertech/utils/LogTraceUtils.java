package com.novatech.cybertech.utils;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns method arguments / return values into short, log-safe summaries for the request trace
 * ({@link com.novatech.cybertech.logger.RequestTraceAspect}).
 *
 * <p>The rule is "identifiers yes, content no": {@link UUID}s (entity ids), numbers, enums, booleans
 * and dates are printed as-is because they are what you grep for when debugging; a UUID-shaped
 * {@link String} is a user identity (Keycloak subject) and is masked through
 * {@link LogSafetyUtils#maskUuid(String)} like everywhere else; free text, DTOs and tokens are
 * reduced to their type (and size) — a DTO's Lombok {@code toString()} would dump
 * card numbers, passwords or emails (see {@link LogSafetyUtils} for the audit that banned it).
 *
 * <p>Like {@link LogSafetyUtils}, every helper is null-safe and never throws: a logging path must
 * never be the reason a request fails.
 */
public final class LogTraceUtils {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final String NULL = "null";
    private static final String ARG_SEPARATOR = ", ";
    private static final String INDENT_UNIT = "  ";
    /** Deeper nesting is still traced, the indentation just stops growing to keep lines readable. */
    private static final int MAX_INDENT_DEPTH = 10;

    private LogTraceUtils() {
        // utility class — no instantiation
    }

    /**
     * Summarises a whole argument list, e.g. {@code (3f2a…-uuid, CartCreateRequestDto, page=0 size=20)}.
     */
    public static String summarizeArgs(final Object[] args) {
        if (args == null || args.length == 0) {
            return "()";
        }
        return Arrays.stream(args)
                .map(LogTraceUtils::summarize)
                .collect(Collectors.joining(ARG_SEPARATOR, "(", ")"));
    }

    /**
     * Summarises a single value. See the class javadoc for what is and is not printed verbatim.
     */
    public static String summarize(final Object value) {
        try {
            return switch (value) {
                case null -> NULL;
                case UUID uuid -> uuid.toString();
                case Number number -> number.toString();
                case Boolean bool -> bool.toString();
                case Enum<?> enumValue -> enumValue.name();
                case Temporal temporal -> temporal.toString();
                case String string -> UUID_PATTERN.matcher(string).matches()
                        ? LogSafetyUtils.maskUuid(string)
                        : "String[" + string.length() + "]";
                case Jwt jwt -> "Jwt";
                case Authentication authentication -> "Authentication";
                case Pageable pageable -> pageable.isPaged()
                        ? "page=" + pageable.getPageNumber() + " size=" + pageable.getPageSize()
                        : "unpaged";
                case Page<?> page -> "Page[" + page.getNumberOfElements() + "/" + page.getTotalElements() + "]";
                case MultipartFile file -> "File[" + file.getSize() + "B]";
                case ResponseEntity<?> response -> "HTTP " + response.getStatusCode().value()
                        + (response.getBody() == null ? "" : " " + summarize(response.getBody()));
                case Optional<?> optional -> optional.map(o -> "Optional[" + summarize(o) + "]").orElse("Optional.empty");
                case Collection<?> collection -> typeName(collection) + "[" + collection.size() + "]";
                case Map<?, ?> map -> "Map[" + map.size() + "]";
                case Object[] array -> "Array[" + array.length + "]";
                default -> typeName(value);
            };
        } catch (RuntimeException e) {
            return typeName(value);
        }
    }

    /**
     * Leading whitespace for a call at the given nesting depth, so the trace reads like a call tree.
     */
    public static String indent(final int depth) {
        return INDENT_UNIT.repeat(Math.clamp(depth, 0, MAX_INDENT_DEPTH));
    }

    private static String typeName(final Object value) {
        return value == null ? NULL : value.getClass().getSimpleName();
    }
}
