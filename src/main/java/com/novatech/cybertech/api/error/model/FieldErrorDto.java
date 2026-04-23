package com.novatech.cybertech.api.error.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Per-field validation failure entry surfaced inside {@link ErrorResponseDto}
 * when a {@code @Valid} controller argument fails Bean Validation. Introduced
 * to close BUG-138 — the previous canned message dropped the
 * {@link org.springframework.validation.BindingResult BindingResult} payload.
 *
 * @see com.novatech.cybertech.api.error.ErrorManagementController#handleMethodArgumentNotValidException
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
public class FieldErrorDto {

    /** Logical name of the rejected field (matches the DTO property path). */
    private final String field;

    /**
     * The value submitted by the caller that triggered the violation; serialised
     * as a string for safe transport (passwords / sensitive values can be
     * scrubbed at the handler level if necessary).
     */
    private final Object rejectedValue;

    /** Human-readable explanation, typically the constraint annotation message. */
    private final String message;
}
