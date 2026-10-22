package com.novatech.cybertech.api.error.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Per-field validation failure entry surfaced inside {@link ErrorResponseDto}
 * when a {@code @Valid} controller argument fails Bean Validation, so a client can
 * attach each message to its form field instead of parsing one concatenated string.
 *
 * <p>The rejected value is deliberately NOT echoed back: it would bounce passwords and card
 * numbers into responses (and into any log or proxy that records them).</p>
 *
 * @see com.novatech.cybertech.api.error.ErrorManagementController#handleMethodArgumentNotValidException
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
public class FieldErrorDto {

    /** Logical name of the rejected field (matches the DTO property path, e.g. {@code cartItemAddRequestDtos[0].quantity}). */
    private final String field;

    /** Human-readable explanation, typically the constraint annotation message. */
    private final String message;
}
