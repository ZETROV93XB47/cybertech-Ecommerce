package com.novatech.cybertech.api.error;


import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.novatech.cybertech.api.error.model.ErrorDetailsDto;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.api.error.model.FieldErrorDto;
import com.novatech.cybertech.api.error.model.StockShortageDto;
import com.novatech.cybertech.exceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
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

import java.util.List;
import java.util.stream.Collectors;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCode.*;

@Slf4j
@ControllerAdvice
public class ErrorManagementController {

    static final String CONCURRENT_MODIFICATION_MESSAGE = "This resource was modified by another request at the same time, please refresh and retry.";

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleAccountNotFoundException(AccountNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), ACCOUNT_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, ACCOUNT_NOT_FOUND.getResponseStatus());
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        final String fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " (" + fe.getDefaultMessage() + ")")
                .collect(Collectors.joining(", "));
        final String message = fieldErrors.isBlank() ? "Invalid Request or Request Poorly Constructed"
                : "Validation failed: " + fieldErrors;
        final List<FieldErrorDto> fieldErrorDtos = exception.getBindingResult().getFieldErrors().stream()
                .map(fe -> FieldErrorDto.builder().field(fe.getField()).message(fe.getDefaultMessage()).build())
                .toList();
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(message, INVALID_REQUEST, fieldErrorDtos);
        return new ResponseEntity<>(errorResponseDto, INVALID_REQUEST.getResponseStatus());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        final String message = "Invalid value for parameter '" + exception.getName() + "'";
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(message, METHOD_ARGUMENT_TYPE_MISMATCH);
        return new ResponseEntity<>(errorResponseDto, METHOD_ARGUMENT_TYPE_MISMATCH.getResponseStatus());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of("Malformed JSON request body", MALFORMED_JSON);
        return new ResponseEntity<>(errorResponseDto, MALFORMED_JSON.getResponseStatus());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoResourceFoundException(NoResourceFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of("The page you're asking for doesn't exists :(", RESOURCE_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, RESOURCE_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponseDto> handleAuthorizationDeniedException(RuntimeException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of("Access denied", ACCESS_DENIED);
        return new ResponseEntity<>(errorResponseDto, ACCESS_DENIED.getResponseStatus());
    }

    @ExceptionHandler(UnauthorizedBankCardAccessException.class)
    public ResponseEntity<ErrorResponseDto> handleUnauthorizedBankCardAccessException(UnauthorizedBankCardAccessException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), ACCESS_DENIED);
        return new ResponseEntity<>(errorResponseDto, ACCESS_DENIED.getResponseStatus());
    }

    /**
     * Maps {@link UnauthorizedCartAccessException} to a 403 FUNCTIONAL
     * error. The exception is raised by {@code CartServiceImp} when an
     * authenticated user attempts to update a cart whose owner's
     * Keycloak subject differs from the caller's — closing the IDOR hole on
     * {@code PATCH /cart/update/{cartUuid}}.
     */
    @ExceptionHandler(UnauthorizedCartAccessException.class)
    public ResponseEntity<ErrorResponseDto> handleUnauthorizedCartAccessException(UnauthorizedCartAccessException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), UNAUTHORIZED_CART_ACCESS);
        return new ResponseEntity<>(errorResponseDto, UNAUTHORIZED_CART_ACCESS.getResponseStatus());
    }

    @ExceptionHandler(CannotRemoveItemFromEmptyCartException.class)
    public ResponseEntity<ErrorResponseDto> handleCannotRemoveItemFromEmptyCartException(CannotRemoveItemFromEmptyCartException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of("Cannot remove item from empty cart.", CANNOT_REMOVE_ITEM_FROM_EMPTY_CART);
        return new ResponseEntity<>(errorResponseDto, CANNOT_REMOVE_ITEM_FROM_EMPTY_CART.getResponseStatus());
    }

    @ExceptionHandler(UnrecognizedPropertyException.class)
    public ResponseEntity<ErrorResponseDto> handleUnrecognizedPropertyException(UnrecognizedPropertyException ex) {
        final String message = "Invalid request: Unknown property '" + ex.getPropertyName() + "'";
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(message, INVALID_REQUEST);
        return new ResponseEntity<>(errorResponseDto, INVALID_REQUEST.getResponseStatus());
    }

    @ExceptionHandler(ProductConstraintsViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleProductConstraintsViolationException(ProductConstraintsViolationException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), INVALID_REQUEST);
        return new ResponseEntity<>(errorResponseDto, INVALID_REQUEST.getResponseStatus());
    }

    @ExceptionHandler(CannotCancelOrderException.class)
    public ResponseEntity<ErrorResponseDto> handleCannotCancelOrderException(CannotCancelOrderException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), CANNOT_CANCEL_ORDER);
        return new ResponseEntity<>(errorResponseDto, CANNOT_CANCEL_ORDER.getResponseStatus());
    }

    @ExceptionHandler(OrderNotFundedException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderNotFundedException(OrderNotFundedException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), ORDER_NOT_FUNDED);
        return new ResponseEntity<>(errorResponseDto, ORDER_NOT_FUNDED.getResponseStatus());
    }

    @ExceptionHandler(OrderRefundFailedException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderRefundFailedException(OrderRefundFailedException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), ORDER_REFUND_FAILED);
        return new ResponseEntity<>(errorResponseDto, ORDER_REFUND_FAILED.getResponseStatus());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleProductNotFoundException(ProductNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), PRODUCT_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, PRODUCT_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(OrderDoesntBelongsToUserException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderDoesntBelongsToUserException(OrderDoesntBelongsToUserException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), ORDER_DOESNT_BELONGS_TO_USER);
        return new ResponseEntity<>(errorResponseDto, ORDER_DOESNT_BELONGS_TO_USER.getResponseStatus());
    }

    @ExceptionHandler(FailedRetryingPayment.class)
    public ResponseEntity<ErrorResponseDto> handleFailedUpdatingOrder(FailedRetryingPayment exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), FAILED_UPDATING_ORDER);
        return new ResponseEntity<>(errorResponseDto, FAILED_UPDATING_ORDER.getResponseStatus());
    }

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleCartNotFoundException(CartNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), CART_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, CART_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(OrderAlreadyShippedException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderAlreadyShippedException(OrderAlreadyShippedException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), ORDER_ALREADY_SHIPPED);
        return new ResponseEntity<>(errorResponseDto, ORDER_ALREADY_SHIPPED.getResponseStatus());
    }

    @ExceptionHandler(NoPreviousPaymentAttemptException.class)
    public ResponseEntity<ErrorResponseDto> handleNoPreviousPaymentAttemptException(NoPreviousPaymentAttemptException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), NO_PREVIOUS_PAYMENT_ATTEMPT_FOUND);
        return new ResponseEntity<>(errorResponseDto, NO_PREVIOUS_PAYMENT_ATTEMPT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(ProductAlreadyInWishlist.class)
    public ResponseEntity<ErrorResponseDto> handleProductAlreadyInWishlist(ProductAlreadyInWishlist exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), PRODUCT_ALREADY_IN_WISHLIST);
        return new ResponseEntity<>(errorResponseDto, PRODUCT_ALREADY_IN_WISHLIST.getResponseStatus());
    }

    @ExceptionHandler(WishlistNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleWishlistNotFoundException(WishlistNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), WISHLIST_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, WISHLIST_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleCartItemNotFoundException(CartItemNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), CART_ITEM_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, CART_ITEM_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(CartIsEmptyException.class)
    public ResponseEntity<ErrorResponseDto> handleCartIsEmptyException(CartIsEmptyException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), CART_IS_EMPTY);
        return new ResponseEntity<>(errorResponseDto, CART_IS_EMPTY.getResponseStatus());
    }

    @ExceptionHandler(AccessTokenRetrievalException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessTokenRetrievalException(AccessTokenRetrievalException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), ACCESS_TOKEN_RETRIEVAL_FAILED);
        return new ResponseEntity<>(errorResponseDto, ACCESS_TOKEN_RETRIEVAL_FAILED.getResponseStatus());
    }

    @ExceptionHandler(BankCardAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleBankCardAlreadyExistsException(BankCardAlreadyExistsException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), BANK_CARD_ALREADY_EXISTS);
        return new ResponseEntity<>(errorResponseDto, BANK_CARD_ALREADY_EXISTS.getResponseStatus());
    }

    @ExceptionHandler(BankCardExpiredException.class)
    public ResponseEntity<ErrorResponseDto> handleBankCardExpiredException(BankCardExpiredException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), BANK_CARD_EXPIRED);
        return new ResponseEntity<>(errorResponseDto, BANK_CARD_EXPIRED.getResponseStatus());
    }

    @ExceptionHandler(BankCardNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleBankCardNotFoundException(BankCardNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), BANK_CARD_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, BANK_CARD_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(CommentPostNotAllowedException.class)
    public ResponseEntity<ErrorResponseDto> handleCommentPostNotAllowedException(CommentPostNotAllowedException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), COMMENT_POST_NOT_ALLOWED);
        return new ResponseEntity<>(errorResponseDto, COMMENT_POST_NOT_ALLOWED.getResponseStatus());
    }

    @ExceptionHandler(IdempotencyKeyGenerationException.class)
    public ResponseEntity<ErrorResponseDto> handleIdempotencyKeyGenerationException(IdempotencyKeyGenerationException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), IDEMPOTENCY_KEY_GENERATION_FAILED);
        return new ResponseEntity<>(errorResponseDto, IDEMPOTENCY_KEY_GENERATION_FAILED.getResponseStatus());
    }

    @ExceptionHandler(NoStrategyFoundForProcessingTheRequest.class)
    public ResponseEntity<ErrorResponseDto> handleNoStrategyFoundForProcessingTheRequest(NoStrategyFoundForProcessingTheRequest exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), NO_STRATEGY_FOUND);
        return new ResponseEntity<>(errorResponseDto, NO_STRATEGY_FOUND.getResponseStatus());
    }

    /** 409 with {@code details.items}: one entry per short line, so the client can flag each of them. */
    @ExceptionHandler(NotEnoughStockException.class)
    public ResponseEntity<ErrorResponseDto> handleNotEnoughStockException(NotEnoughStockException exception) {
        final List<StockShortageDto> items = exception.getShortages().stream()
                .map(shortage -> new StockShortageDto(shortage.productUuid(), shortage.requested(), shortage.available()))
                .toList();
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.withDetails(exception.getMessage(), NOT_ENOUGH_STOCK, new ErrorDetailsDto(items));
        return new ResponseEntity<>(errorResponseDto, NOT_ENOUGH_STOCK.getResponseStatus());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderNotFoundException(OrderNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), ORDER_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, ORDER_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(OrderSummuryReportJobFailedException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderSummaryReportJobFailedException(OrderSummuryReportJobFailedException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), ORDER_SUMMARY_REPORT_JOB_FAILED);
        return new ResponseEntity<>(errorResponseDto, ORDER_SUMMARY_REPORT_JOB_FAILED.getResponseStatus());
    }

    @ExceptionHandler(PaymentAlreadyCompletedForThisOrderException.class)
    public ResponseEntity<ErrorResponseDto> handlePaymentAlreadyCompletedException(PaymentAlreadyCompletedForThisOrderException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), PAYMENT_ALREADY_COMPLETED);
        return new ResponseEntity<>(errorResponseDto, PAYMENT_ALREADY_COMPLETED.getResponseStatus());
    }

    /**
     * Two requests modified the same row concurrently (JPA {@code @Version} mismatch, or a row
     * already deleted by the other one) — e.g. a double-clicked "place order" whose second request
     * finds the cart lines already consumed by the first, or two concurrent writes on the same
     * cart line. A retryable conflict, not a server fault: 409 with a fixed message, never the
     * Hibernate one (it leaks entity/row details).
     */
    /**
     * The per-user Redis cart lock could not be acquired in time — another request on the same cart
     * is in flight. Same contract as an optimistic-lock conflict: retryable 409, never a 500.
     */
    @ExceptionHandler(CartLockedException.class)
    public ResponseEntity<ErrorResponseDto> handleCartLockedException(CartLockedException exception) {
        log.info("Cart write rejected, lock busy: {}", exception.getMessage());
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(CONCURRENT_MODIFICATION_MESSAGE, CONCURRENT_MODIFICATION);
        return new ResponseEntity<>(errorResponseDto, CONCURRENT_MODIFICATION.getResponseStatus());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDto> handleOptimisticLockingFailureException(OptimisticLockingFailureException exception) {
        log.info("Concurrent modification rejected: {}", exception.getMessage());
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(CONCURRENT_MODIFICATION_MESSAGE, CONCURRENT_MODIFICATION);
        return new ResponseEntity<>(errorResponseDto, CONCURRENT_MODIFICATION.getResponseStatus());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ErrorResponseDto> handlePaymentFailedException(PaymentFailedException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), PAYMENT_FAILED);
        return new ResponseEntity<>(errorResponseDto, PAYMENT_FAILED.getResponseStatus());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handlePaymentNotFoundException(PaymentNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), PAYMENT_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, PAYMENT_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponseDto> handlePaymentProcessingException(PaymentProcessingException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), PAYMENT_PROCESSING_ERROR);
        return new ResponseEntity<>(errorResponseDto, PAYMENT_PROCESSING_ERROR.getResponseStatus());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleUserAlreadyExistsException(UserAlreadyExistsException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), USER_ALREADY_EXISTS);
        return new ResponseEntity<>(errorResponseDto, USER_ALREADY_EXISTS.getResponseStatus());
    }

    @ExceptionHandler(UserNotActiveException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotActiveException(UserNotActiveException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), USER_NOT_ACTIVE);
        return new ResponseEntity<>(errorResponseDto, USER_NOT_ACTIVE.getResponseStatus());
    }

    @ExceptionHandler(DiscountTypeNotActiveException.class)
    public ResponseEntity<ErrorResponseDto> handleDiscountTypeNotActiveException(DiscountTypeNotActiveException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), DISCOUNT_TYPE_NOT_ACTIVE);
        return new ResponseEntity<>(errorResponseDto, DISCOUNT_TYPE_NOT_ACTIVE.getResponseStatus());
    }

    @ExceptionHandler(DiscountCampaignNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleDiscountCampaignNotFoundException(DiscountCampaignNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), DISCOUNT_CAMPAIGN_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, DISCOUNT_CAMPAIGN_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(DiscountCampaignAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleDiscountCampaignAlreadyExistsException(DiscountCampaignAlreadyExistsException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), DISCOUNT_CAMPAIGN_ALREADY_EXISTS);
        return new ResponseEntity<>(errorResponseDto, DISCOUNT_CAMPAIGN_ALREADY_EXISTS.getResponseStatus());
    }

    @ExceptionHandler(DiscountCampaignMissingRequiredFieldException.class)
    public ResponseEntity<ErrorResponseDto> handleDiscountCampaignMissingRequiredFieldException(DiscountCampaignMissingRequiredFieldException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), DISCOUNT_CAMPAIGN_MISSING_REQUIRED_FIELD);
        return new ResponseEntity<>(errorResponseDto, DISCOUNT_CAMPAIGN_MISSING_REQUIRED_FIELD.getResponseStatus());
    }

    @ExceptionHandler(DiscountTypeCannotBeNullForStrategy.class)
    public ResponseEntity<ErrorResponseDto> handleDiscountTypeCannotBeNullForStrategy(DiscountTypeCannotBeNullForStrategy exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), NO_STRATEGY_FOUND);
        return new ResponseEntity<>(errorResponseDto, NO_STRATEGY_FOUND.getResponseStatus());
    }

    @ExceptionHandler(NegativeQuantityException.class)
    public ResponseEntity<ErrorResponseDto> handleNegativeQuantityException(NegativeQuantityException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), INVALID_REQUEST);
        return new ResponseEntity<>(errorResponseDto, INVALID_REQUEST.getResponseStatus());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), INVALID_REQUEST);
        return new ResponseEntity<>(errorResponseDto, INVALID_REQUEST.getResponseStatus());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDto> handleRuntimeException(RuntimeException exception) {
        // The raw exception message can carry SQL fragments, stack traces, internal
        // paths or leaked secrets. Log it server-side in full, but return a sanitized generic
        // message to the client.
        log.error("Unhandled exception surfaced to @ControllerAdvice", exception);
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of("An unexpected error occurred. Please contact support if the issue persists.", APPLICATION_ERROR);
        return new ResponseEntity<>(errorResponseDto, APPLICATION_ERROR.getResponseStatus());
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleReviewNotFoundException(ReviewNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), REVIEW_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, REVIEW_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotFoundException(UserNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), USER_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, USER_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(UserNotAuthorOfReviewException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotAuthorOfReviewException(UserNotAuthorOfReviewException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), USER_NOT_AUTHOR_OF_REVIEW);
        return new ResponseEntity<>(errorResponseDto, USER_NOT_AUTHOR_OF_REVIEW.getResponseStatus());
    }

    /**
     * Maps {@link OrderNotReviewableException} to HTTP 409 CONFLICT. Raised when a user attempts to
     * post a review against an order that has not yet reached a reviewable lifecycle state
     * (only PAID / SHIPPED / DELIVERED orders can be reviewed).
     */
    @ExceptionHandler(OrderNotReviewableException.class)
    public ResponseEntity<ErrorResponseDto> handleOrderNotReviewableException(OrderNotReviewableException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), ORDER_NOT_REVIEWABLE);
        return new ResponseEntity<>(errorResponseDto, ORDER_NOT_REVIEWABLE.getResponseStatus());
    }

    @ExceptionHandler(UnknownProductCategoryException.class)
    public ResponseEntity<ErrorResponseDto> handleUnknownProductCategoryException(UnknownProductCategoryException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), UNKNOWN_PRODUCT_CATEGORY);
        return new ResponseEntity<>(errorResponseDto, UNKNOWN_PRODUCT_CATEGORY.getResponseStatus());
    }

    @ExceptionHandler(InvalidProductCategorySchemaException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidProductCategorySchemaException(InvalidProductCategorySchemaException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), INVALID_PRODUCT_CATEGORY_SCHEMA);
        return new ResponseEntity<>(errorResponseDto, INVALID_PRODUCT_CATEGORY_SCHEMA.getResponseStatus());
    }

    @ExceptionHandler(PushServiceNotConfiguredException.class)
    public ResponseEntity<ErrorResponseDto> handlePushServiceNotConfiguredException(PushServiceNotConfiguredException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), PUSH_SERVICE_NOT_CONFIGURED);
        return new ResponseEntity<>(errorResponseDto, PUSH_SERVICE_NOT_CONFIGURED.getResponseStatus());
    }

    @ExceptionHandler(PushSubscriptionConflictException.class)
    public ResponseEntity<ErrorResponseDto> handlePushSubscriptionConflictException(PushSubscriptionConflictException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), PUSH_SUBSCRIPTION_CONFLICT);
        return new ResponseEntity<>(errorResponseDto, PUSH_SUBSCRIPTION_CONFLICT.getResponseStatus());
    }

    @ExceptionHandler(InvalidPushEndpointException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidPushEndpointException(InvalidPushEndpointException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), INVALID_PUSH_ENDPOINT);
        return new ResponseEntity<>(errorResponseDto, INVALID_PUSH_ENDPOINT.getResponseStatus());
    }

    @ExceptionHandler(PushSubscriptionNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handlePushSubscriptionNotFoundException(PushSubscriptionNotFoundException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), PUSH_SUBSCRIPTION_NOT_FOUND);
        return new ResponseEntity<>(errorResponseDto, PUSH_SUBSCRIPTION_NOT_FOUND.getResponseStatus());
    }

    @ExceptionHandler(PushSubscriptionNotOwnedException.class)
    public ResponseEntity<ErrorResponseDto> handlePushSubscriptionNotOwnedException(PushSubscriptionNotOwnedException exception) {
        final ErrorResponseDto errorResponseDto = ErrorResponseDto.of(exception.getMessage(), PUSH_SUBSCRIPTION_NOT_OWNED);
        return new ResponseEntity<>(errorResponseDto, PUSH_SUBSCRIPTION_NOT_OWNED.getResponseStatus());
    }
}
