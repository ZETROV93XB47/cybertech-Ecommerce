package com.novatech.cybertech.api.error;


import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.exceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCode.*;

@Slf4j
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        final String message = "Invalid value for parameter '" + exception.getName() + "'";
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(message, METHOD_ARGUMENT_TYPE_MISMATCH.getResponseStatus().value(), METHOD_ARGUMENT_TYPE_MISMATCH.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, METHOD_ARGUMENT_TYPE_MISMATCH.getResponseStatus());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto("Malformed JSON request body", MALFORMED_JSON.getResponseStatus().value(), MALFORMED_JSON.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, MALFORMED_JSON.getResponseStatus());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoResourceFoundException(NoResourceFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto("The page you're asking for doesn't exists :(", RESOURCE_NOT_FOUND.getResponseStatus().value(), RESOURCE_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, RESOURCE_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponseDto> handleAuthorizationDeniedException(RuntimeException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto("Access denied", ACCESS_DENIED.getResponseStatus().value(), ACCESS_DENIED.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, ACCESS_DENIED.getResponseStatus());
    }

    @ExceptionHandler(UnauthorizedBankCardAccessException.class)
    public ResponseEntity<ErrorResponseDto> handleUnauthorizedBankCardAccessException(UnauthorizedBankCardAccessException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), ACCESS_DENIED.getResponseStatus().value(), ACCESS_DENIED.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, ACCESS_DENIED.getResponseStatus());
    }

    /**
     * BUG-161 — Maps {@link UnauthorizedCartAccessException} to a 403 FUNCTIONAL
     * error. The exception is raised by {@code CartServiceImp} when an
     * authenticated user attempts to read, update or delete a cart whose owner's
     * Keycloak subject differs from the caller's — closing the IDOR hole on
     * {@code GET /cart/get/{cartUuid}}, {@code PATCH /cart/update/{cartUuid}}
     * and {@code DELETE /cart/delete/{cartUuid}}.
     */
    @ExceptionHandler(UnauthorizedCartAccessException.class)
    public ResponseEntity<ErrorResponseDto> handleUnauthorizedCartAccessException(UnauthorizedCartAccessException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), UNAUTHORIZED_CART_ACCESS.getResponseStatus().value(), UNAUTHORIZED_CART_ACCESS.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, UNAUTHORIZED_CART_ACCESS.getResponseStatus());
    }

    @ExceptionHandler(CannotRemoveItemFromEmptyCartException.class)
    public ResponseEntity<ErrorResponseDto> handleCannotRemoveItemFromEmptyCartException(CannotRemoveItemFromEmptyCartException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto("Cannot remove item from empty cart.", CANNOT_REMOVE_ITEM_FROM_EMPTY_CART.getResponseStatus().value(), CANNOT_REMOVE_ITEM_FROM_EMPTY_CART.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, CANNOT_REMOVE_ITEM_FROM_EMPTY_CART.getResponseStatus());
    }

    @ExceptionHandler(UnrecognizedPropertyException.class)
    public ResponseEntity<ErrorResponseDto> handleUnrecognizedPropertyException(UnrecognizedPropertyException ex) {
        final String message = "Invalid request: Unknown property '" + ex.getPropertyName() + "'";
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(message, INVALID_REQUEST.getResponseStatus().value(), INVALID_REQUEST.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, INVALID_REQUEST.getResponseStatus());
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
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), PRODUCT_NOT_FOUND.getResponseStatus().value(), PRODUCT_NOT_FOUND.getErrorCodeType());
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
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), CART_NOT_FOUND.getResponseStatus().value(), CART_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, CART_NOT_FOUND.getResponseStatus());
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

    @ExceptionHandler(ProductAlreadyInWishlist.class)
    public ResponseEntity<ErrorResponseDto> handleProductAlreadyInWishlist(ProductAlreadyInWishlist exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), PRODUCT_ALREADY_IN_WISHLIST.getResponseStatus().value(), PRODUCT_ALREADY_IN_WISHLIST.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, PRODUCT_ALREADY_IN_WISHLIST.getResponseStatus());
    }

    @ExceptionHandler(WishlistNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleWishlistNotFoundException(WishlistNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), WISHLIST_NOT_FOUND.getResponseStatus().value(), WISHLIST_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, WISHLIST_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleCartItemNotFoundException(CartItemNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), CART_ITEM_NOT_FOUND.getResponseStatus().value(), CART_ITEM_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, CART_ITEM_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(CartIsEmptyException.class)
    public ResponseEntity<ErrorResponseDto> handleCartIsEmptyException(CartIsEmptyException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), CART_IS_EMPTY.getResponseStatus().value(), CART_IS_EMPTY.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, CART_IS_EMPTY.getResponseStatus());
    }

    @ExceptionHandler(AccessTokenRetrievalException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessTokenRetrievalException(AccessTokenRetrievalException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), ACCESS_TOKEN_RETRIEVAL_FAILED.getResponseStatus().value(), ACCESS_TOKEN_RETRIEVAL_FAILED.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, ACCESS_TOKEN_RETRIEVAL_FAILED.getResponseStatus());
    }

    @ExceptionHandler(BankCardExpiredException.class)
    public ResponseEntity<ErrorResponseDto> handleBankCardExpiredException(BankCardExpiredException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), BANK_CARD_EXPIRED.getResponseStatus().value(), BANK_CARD_EXPIRED.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, BANK_CARD_EXPIRED.getResponseStatus());
    }

    @ExceptionHandler(BankCardNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleBankCardNotFoundException(BankCardNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), BANK_CARD_NOT_FOUND.getResponseStatus().value(), BANK_CARD_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, BANK_CARD_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(CommentPostNotAllowedException.class)
    public ResponseEntity<ErrorResponseDto> handleCommentPostNotAllowedException(CommentPostNotAllowedException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), COMMENT_POST_NOT_ALLOWED.getResponseStatus().value(), COMMENT_POST_NOT_ALLOWED.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, COMMENT_POST_NOT_ALLOWED.getResponseStatus());
    }

    @ExceptionHandler(IdempotencyKeyGenerationException.class)
    public ResponseEntity<ErrorResponseDto> handleIdempotencyKeyGenerationException(IdempotencyKeyGenerationException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), IDEMPOTENCY_KEY_GENERATION_FAILED.getResponseStatus().value(), IDEMPOTENCY_KEY_GENERATION_FAILED.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, IDEMPOTENCY_KEY_GENERATION_FAILED.getResponseStatus());
    }

    @ExceptionHandler(NoDefaultBankCartSetException.class)
    public ResponseEntity<ErrorResponseDto> handleNoDefaultBankCartSetException(NoDefaultBankCartSetException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), NO_DEFAULT_BANK_CARD_SET.getResponseStatus().value(), NO_DEFAULT_BANK_CARD_SET.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, NO_DEFAULT_BANK_CARD_SET.getResponseStatus());
    }

    @ExceptionHandler(NoStrategyFoundForProcessingTheRequest.class)
    public ResponseEntity<ErrorResponseDto> handleNoStrategyFoundForProcessingTheRequest(NoStrategyFoundForProcessingTheRequest exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), NO_STRATEGY_FOUND.getResponseStatus().value(), NO_STRATEGY_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, NO_STRATEGY_FOUND.getResponseStatus());
    }

    @ExceptionHandler(NotEnoughStockException.class)
    public ResponseEntity<ErrorResponseDto> handleNotEnoughStockException(NotEnoughStockException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), NOT_ENOUGH_STOCK.getResponseStatus().value(), NOT_ENOUGH_STOCK.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, NOT_ENOUGH_STOCK.getResponseStatus());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderNotFoundException(OrderNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), ORDER_NOT_FOUND.getResponseStatus().value(), ORDER_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, ORDER_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(OrderSummuryReportJobFailedException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderSummaryReportJobFailedException(OrderSummuryReportJobFailedException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), ORDER_SUMMARY_REPORT_JOB_FAILED.getResponseStatus().value(), ORDER_SUMMARY_REPORT_JOB_FAILED.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, ORDER_SUMMARY_REPORT_JOB_FAILED.getResponseStatus());
    }

    @ExceptionHandler(PaymentAlreadyCompletedForThisOrderException.class)
    public ResponseEntity<ErrorResponseDto> handlePaymentAlreadyCompletedException(PaymentAlreadyCompletedForThisOrderException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), PAYMENT_ALREADY_COMPLETED.getResponseStatus().value(), PAYMENT_ALREADY_COMPLETED.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, PAYMENT_ALREADY_COMPLETED.getResponseStatus());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ErrorResponseDto> handlePaymentFailedException(PaymentFailedException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), PAYMENT_FAILED.getResponseStatus().value(), PAYMENT_FAILED.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, PAYMENT_FAILED.getResponseStatus());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handlePaymentNotFoundException(PaymentNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), PAYMENT_NOT_FOUND.getResponseStatus().value(), PAYMENT_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, PAYMENT_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponseDto> handlePaymentProcessingException(PaymentProcessingException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), PAYMENT_PROCESSING_ERROR.getResponseStatus().value(), PAYMENT_PROCESSING_ERROR.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, PAYMENT_PROCESSING_ERROR.getResponseStatus());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleUserAlreadyExistsException(UserAlreadyExistsException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), USER_ALREADY_EXISTS.getResponseStatus().value(), USER_ALREADY_EXISTS.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, USER_ALREADY_EXISTS.getResponseStatus());
    }

    @ExceptionHandler(UserNotActiveException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotActiveException(UserNotActiveException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), USER_NOT_ACTIVE.getResponseStatus().value(), USER_NOT_ACTIVE.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, USER_NOT_ACTIVE.getResponseStatus());
    }

    @ExceptionHandler(DiscountTypeNotActiveException.class)
    public ResponseEntity<ErrorResponseDto> handleDiscountTypeNotActiveException(DiscountTypeNotActiveException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), DISCOUNT_TYPE_NOT_ACTIVE.getResponseStatus().value(), DISCOUNT_TYPE_NOT_ACTIVE.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, DISCOUNT_TYPE_NOT_ACTIVE.getResponseStatus());
    }

    @ExceptionHandler(DiscountTypeCannotBeNullForStrategy.class)
    public ResponseEntity<ErrorResponseDto> handleDiscountTypeCannotBeNullForStrategy(DiscountTypeCannotBeNullForStrategy exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), NO_STRATEGY_FOUND.getResponseStatus().value(), NO_STRATEGY_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, NO_STRATEGY_FOUND.getResponseStatus());
    }

    @ExceptionHandler(NegativeQuantityException.class)
    public ResponseEntity<ErrorResponseDto> handleNegativeQuantityException(NegativeQuantityException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), INVALID_REQUEST.getResponseStatus().value(), INVALID_REQUEST.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, INVALID_REQUEST.getResponseStatus());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), INVALID_REQUEST.getResponseStatus().value(), INVALID_REQUEST.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, INVALID_REQUEST.getResponseStatus());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDto> handleRuntimeException(RuntimeException exception) {
        // BUG-140 fix: the raw exception message can carry SQL fragments, stack traces, internal
        // paths or leaked secrets. Log it server-side in full, but return a sanitized generic
        // message to the client.
        log.error("Unhandled exception surfaced to @ControllerAdvice", exception);
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(
                "An unexpected error occurred. Please contact support if the issue persists.",
                APPLICATION_ERROR.getResponseStatus().value(),
                APPLICATION_ERROR.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, APPLICATION_ERROR.getResponseStatus());
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleReviewNotFoundException(ReviewNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), REVIEW_NOT_FOUND.getResponseStatus().value(), REVIEW_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, REVIEW_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotFoundException(UserNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), USER_NOT_FOUND.getResponseStatus().value(), USER_NOT_FOUND.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, USER_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(UserNotAuthorOfReviewException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotAuthorOfReviewException(UserNotAuthorOfReviewException exception) {
        final ErrorResponseDto errorResponseDto = new ErrorResponseDto(exception.getMessage(), USER_NOT_AUTHOR_OF_REVIEW.getResponseStatus().value(), USER_NOT_AUTHOR_OF_REVIEW.getErrorCodeType());
        return new ResponseEntity<>(errorResponseDto, USER_NOT_AUTHOR_OF_REVIEW.getResponseStatus());
    }
}
