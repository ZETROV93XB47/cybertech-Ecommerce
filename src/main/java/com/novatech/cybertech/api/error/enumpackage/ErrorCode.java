package com.novatech.cybertech.api.error.enumpackage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    APPLICATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL),
    /** No / invalid / expired bearer token (CustomAuthenticationEntryPoint). */
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, ErrorCodeType.TECHNICAL),
    /** RateLimitFilter quota exhausted — the Retry-After header carries the wait in seconds. */
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, ErrorCodeType.TECHNICAL),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.TECHNICAL),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    // Business state conflict, not authorization denial — use 409 instead of 403
    CANNOT_CANCEL_ORDER(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    /** Removing a line that is not in the cart: a state conflict, not an authorization failure (was 403). */
    CANNOT_REMOVE_ITEM_FROM_EMPTY_CART(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    ORDER_DOESNT_BELONGS_TO_USER(HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL),
    // Retry/business failure, not authorization denial — use 422 instead of 403
    FAILED_UPDATING_ORDER(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodeType.FUNCTIONAL),
    /** Ordering or decreasing from an empty cart: a state conflict, not an authorization failure (was 403). */
    CART_IS_EMPTY(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL),
    METHOD_ARGUMENT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL),
    // Business state conflict, not authorization denial — use 409 instead of 403
    ORDER_ALREADY_SHIPPED(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    ORDER_NOT_FUNDED(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    ORDER_REFUND_FAILED(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    NO_PREVIOUS_PAYMENT_ATTEMPT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    PRODUCT_ALREADY_IN_WISHLIST(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    USER_NOT_ACTIVE(HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    USER_NOT_AUTHOR_OF_REVIEW(HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL),
    WISHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    ACCESS_TOKEN_RETRIEVAL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL),
    BANK_CARD_EXPIRED(HttpStatus.BAD_REQUEST, ErrorCodeType.FUNCTIONAL),
    BANK_CARD_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    COMMENT_POST_NOT_ALLOWED(HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL),
    IDEMPOTENCY_KEY_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL),
    NO_STRATEGY_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL),
    NOT_ENOUGH_STOCK(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    ORDER_SUMMARY_REPORT_JOB_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL),
    /** Optimistic-lock conflict: two requests modified the same row concurrently (retryable). */
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, ErrorCodeType.FUNCTIONAL),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    PAYMENT_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL),
    DISCOUNT_TYPE_NOT_ACTIVE(HttpStatus.BAD_REQUEST, ErrorCodeType.FUNCTIONAL),
    /** Authenticated user tried to read/update/delete a cart they do not own (IDOR). */
    UNAUTHORIZED_CART_ACCESS(HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL),
    /** Order is not in a status that allows reviewing (only PAID/SHIPPED/DELIVERED are reviewable). */
    ORDER_NOT_REVIEWABLE(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    /** A product/search request references a categoryKey with no registered schema. */
    UNKNOWN_PRODUCT_CATEGORY(HttpStatus.BAD_REQUEST, ErrorCodeType.FUNCTIONAL),
    /** Admin submitted a categoryKey/jsonSchema pair that fails to compile as a JSON Schema. */
    INVALID_PRODUCT_CATEGORY_SCHEMA(HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL),
    /** Admin created/updated a discount campaign missing the field its calculationType requires (e.g. PERCENTAGE with no percentage). */
    DISCOUNT_CAMPAIGN_MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, ErrorCodeType.FUNCTIONAL),
    /** Admin looked up / updated / deleted a discount campaign key that does not exist. */
    DISCOUNT_CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    /** Admin tried to create a discount campaign under a key that is already taken. */
    DISCOUNT_CAMPAIGN_ALREADY_EXISTS(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    PUSH_SERVICE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodeType.TECHNICAL),
    PUSH_SUBSCRIPTION_CONFLICT(HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL),
    INVALID_PUSH_ENDPOINT(HttpStatus.BAD_REQUEST, ErrorCodeType.FUNCTIONAL),
    /** Unsubscribe called with a subscription UUID that does not exist (or was already replaced). */
    PUSH_SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL),
    /** Unsubscribe called on a subscription registered by another account. */
    PUSH_SUBSCRIPTION_NOT_OWNED(HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL);

    private final HttpStatus responseStatus;
    private final ErrorCodeType errorCodeType;
}
