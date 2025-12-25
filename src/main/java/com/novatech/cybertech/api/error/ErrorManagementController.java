package com.novatech.cybertech.api.error;


import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.exceptions.AccountNotFoundException;
import com.novatech.cybertech.exceptions.CannotCancelOrderException;
import com.novatech.cybertech.exceptions.ProductConstraintsViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCode.ACCOUNT_NOT_FOUND;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCode.CANNOT_CANCEL_ORDER;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCode.INVALID_REQUEST;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCode.RESOURCE_NOT_FOUND;


@ControllerAdvice
public class ErrorManagementController {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleAccountNotFoundException(AccountNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), ACCOUNT_NOT_FOUND.getResponseStatus().value(), ACCOUNT_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, ACCOUNT_NOT_FOUND.getResponseStatus());
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto("Invalid Request or Request Poorly Constructed", INVALID_REQUEST.getResponseStatus().value(), INVALID_REQUEST.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, INVALID_REQUEST.getResponseStatus());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoResourceFoundException(NoResourceFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto("The page you're asking for doesn't exists :(", RESOURCE_NOT_FOUND.getResponseStatus().value(), RESOURCE_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, RESOURCE_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(UnrecognizedPropertyException.class)
    public ResponseEntity<String> handleUnrecognizedPropertyException(UnrecognizedPropertyException ex) {
        return new ResponseEntity<>("Invalid request: Unknown property '" + ex.getPropertyName() + "'", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ProductConstraintsViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleProductConstraintsViolationException(ProductConstraintsViolationException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto("The product you're trying to save's attributes aren't matching the constraints criterias, please check your product category and attributes fields to make sure that they're matching together", INVALID_REQUEST.getResponseStatus().value(), INVALID_REQUEST.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, INVALID_REQUEST.getResponseStatus());
    }

    @ExceptionHandler(CannotCancelOrderException.class)
    public ResponseEntity<ErrorResponseDto> handleCannotCancelOrderException(CannotCancelOrderException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto("This Order cannot be cancelled because of it's current status, please consider initiating a return process to get it refunded", CANNOT_CANCEL_ORDER.getResponseStatus().value(), CANNOT_CANCEL_ORDER.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, CANNOT_CANCEL_ORDER.getResponseStatus());
    }
}