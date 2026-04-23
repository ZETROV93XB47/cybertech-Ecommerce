package com.novatech.cybertech.api.error.enumpackage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke + structural test for {@link ErrorCode}. Pure enum, no behavior beyond
 * the Lombok-generated getters; we assert each entry exposes a non-null
 * {@code responseStatus} and {@code errorCodeType}, plus that a few load-bearing
 * mappings are stable (controller-advice depends on them).
 */
class ErrorCodeTest {

    @Test
    @DisplayName("values() exposes the full enum surface (>= the originally-shipped 39 entries)")
    void valuesIsNonEmpty() {
        assertThat(ErrorCode.values()).isNotEmpty();
        // F2 wave added several entries (ACCESS_TOKEN_RETRIEVAL_FAILED, BANK_CARD_EXPIRED, etc.).
        // Lock to "at least 39" — the original surface — without making the test brittle to additions.
        assertThat(ErrorCode.values().length).isGreaterThanOrEqualTo(39);
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("every entry exposes non-null responseStatus + errorCodeType")
    void everyEntryHasNonNullStatusAndType(final ErrorCode code) {
        assertThat(code.getResponseStatus()).isNotNull();
        assertThat(code.getErrorCodeType()).isNotNull();
    }

    @Test
    @DisplayName("load-bearing entries map to the expected HTTP status")
    void loadBearingEntriesMapToExpectedStatus() {
        assertThat(ErrorCode.APPLICATION_ERROR.getResponseStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ErrorCode.APPLICATION_ERROR.getErrorCodeType()).isEqualTo(ErrorCodeType.TECHNICAL);

        assertThat(ErrorCode.INVALID_REQUEST.getResponseStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INVALID_REQUEST.getErrorCodeType()).isEqualTo(ErrorCodeType.TECHNICAL);

        assertThat(ErrorCode.MALFORMED_JSON.getResponseStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.MALFORMED_JSON.getErrorCodeType()).isEqualTo(ErrorCodeType.TECHNICAL);

        assertThat(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH.getResponseStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH.getErrorCodeType()).isEqualTo(ErrorCodeType.TECHNICAL);

        assertThat(ErrorCode.ACCESS_DENIED.getResponseStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.ACCESS_DENIED.getErrorCodeType()).isEqualTo(ErrorCodeType.FUNCTIONAL);

        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getResponseStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.PAYMENT_FAILED.getResponseStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
    }

    @Test
    @DisplayName("F2-added entries are present (verifying F2 fix claim for BUG-001..016)")
    void f2AddedEntriesArePresent() {
        // BUG-001..016 were closed in Wave F2 by adding both the ErrorCode entries and the handlers.
        // This test confirms the ErrorCode side; the controller branch test confirms the handler side.
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
                .contains(
                        "ACCESS_TOKEN_RETRIEVAL_FAILED",
                        "BANK_CARD_EXPIRED",
                        "BANK_CARD_NOT_FOUND",
                        "COMMENT_POST_NOT_ALLOWED",
                        "IDEMPOTENCY_KEY_GENERATION_FAILED",
                        "NO_STRATEGY_FOUND",
                        "NOT_ENOUGH_STOCK",
                        "ORDER_NOT_FOUND",
                        "ORDER_SUMMARY_REPORT_JOB_FAILED",
                        "PAYMENT_ALREADY_COMPLETED",
                        "PAYMENT_FAILED",
                        "PAYMENT_NOT_FOUND",
                        "PAYMENT_PROCESSING_ERROR",
                        "USER_ALREADY_EXISTS",
                        "METHOD_ARGUMENT_TYPE_MISMATCH",
                        "MALFORMED_JSON",
                        "DISCOUNT_TYPE_NOT_ACTIVE"
                );
    }
}
