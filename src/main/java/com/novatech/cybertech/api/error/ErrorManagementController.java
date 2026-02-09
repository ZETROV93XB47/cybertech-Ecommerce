package com.novatech.cybertech.api.error;


import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCode.*;


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

    @ExceptionHandler(CannotRemoveItemFromEmptyCartException.class)
    public ResponseEntity<ErrorResponseDto> handleCannotRemoveItemFromEmptyCartException(CannotRemoveItemFromEmptyCartException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto("Cannot remove item from empty cart.", CANNOT_REMOVE_ITEM_FROM_EMPTY_CART.getResponseStatus().value(), CANNOT_REMOVE_ITEM_FROM_EMPTY_CART.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, CANNOT_REMOVE_ITEM_FROM_EMPTY_CART.getResponseStatus());
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
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), CANNOT_CANCEL_ORDER.getResponseStatus().value(), CANNOT_CANCEL_ORDER.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, CANNOT_CANCEL_ORDER.getResponseStatus());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleProductNotFoundException(ProductNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), RESOURCE_NOT_FOUND.getResponseStatus().value(), PRODUCT_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, PRODUCT_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(OrderDoesntBelongsToUserException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderDoesntBelongsToUserException(OrderDoesntBelongsToUserException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), ORDER_DOESNT_BELONGS_TO_USER.getResponseStatus().value(), ORDER_DOESNT_BELONGS_TO_USER.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, ORDER_DOESNT_BELONGS_TO_USER.getResponseStatus());
    }

    @ExceptionHandler(FailedRetryingPayment.class)
    public ResponseEntity<ErrorResponseDto> handleFailedUpdatingOrder(FailedRetryingPayment exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), FAILED_UPDATING_ORDER.getResponseStatus().value(), FAILED_UPDATING_ORDER.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, FAILED_UPDATING_ORDER.getResponseStatus());
    }

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleCartNotFoundException(CartNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), CART_IS_EMPTY.getResponseStatus().value(), CART_IS_EMPTY.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, CART_IS_EMPTY.getResponseStatus());
    }

    @ExceptionHandler(OrderAlreadyShippedException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderAlreadyShippedException(OrderAlreadyShippedException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), ORDER_ALREADY_SHIPPED.getResponseStatus().value(), ORDER_ALREADY_SHIPPED.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, ORDER_ALREADY_SHIPPED.getResponseStatus());
    }

    @ExceptionHandler(NoPreviousPaymentAttemptException.class)
    public ResponseEntity<ErrorResponseDto> handleNoPreviousPaymentAttemptException(NoPreviousPaymentAttemptException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), NO_PREVIOUS_PAYMENT_ATTEMPT_FOUND.getResponseStatus().value(), NO_PREVIOUS_PAYMENT_ATTEMPT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, NO_PREVIOUS_PAYMENT_ATTEMPT_FOUND.getResponseStatus());
    }

    // Gestionnaire global pour toutes les erreurs non prévues (500)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDto> handleRuntimeException(RuntimeException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto("An unexpected error occurred: " + exception.getMessage(), APPLICATION_ERROR.getResponseStatus().value(), APPLICATION_ERROR.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, APPLICATION_ERROR.getResponseStatus());
    }
}