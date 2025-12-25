package com.novatech.cybertech.api.error.enumpackage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    APPLICATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.TECHNICAL),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    CANNOT_CANCEL_ORDER(HttpStatus.FORBIDDEN, ErrorCodeType.TECHNICAL),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL);

    private final HttpStatus responseStatus;
    private final ErrorCodeType errorCodeType;
}
