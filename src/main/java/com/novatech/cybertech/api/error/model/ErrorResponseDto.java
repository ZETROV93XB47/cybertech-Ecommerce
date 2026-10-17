package com.novatech.cybertech.api.error.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novatech.cybertech.api.error.enumpackage.ErrorCode;
import com.novatech.cybertech.api.error.enumpackage.ErrorCodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

/**
 * Body of every error response. {@code errorCode} is the stable, machine-readable discriminator a
 * client must switch on ({@code message} is human-readable and may change); {@code fieldErrors} is
 * only present on Bean Validation failures.
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
public class ErrorResponseDto {
    private final String message;
    private final int httpStatusCode;
    private final ErrorCodeType errorCodeType;
    private final ErrorCode errorCode;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<FieldErrorDto> fieldErrors;

    /** Builds the body for {@code errorCode}, status and type derived from the code itself. */
    public static ErrorResponseDto of(final String message, final ErrorCode errorCode) {
        return of(message, errorCode, null);
    }

    public static ErrorResponseDto of(final String message, final ErrorCode errorCode, final List<FieldErrorDto> fieldErrors) {
        return new ErrorResponseDto(message, errorCode.getResponseStatus().value(), errorCode.getErrorCodeType(), errorCode, fieldErrors);
    }
}
