package com.novatech.cybertech.api.error.model;

import com.novatech.cybertech.api.error.enumpackage.ErrorCodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
public class ErrorResponseDto {
    private final String message;
    private final int httpStatusCode;
    private final ErrorCodeType errorCodeType;
}