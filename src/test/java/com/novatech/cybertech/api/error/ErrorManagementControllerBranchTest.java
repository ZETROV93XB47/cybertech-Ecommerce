package com.novatech.cybertech.api.error;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.novatech.cybertech.api.error.enumpackage.ErrorCodeType;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.exceptions.AccessTokenRetrievalException;
import com.novatech.cybertech.exceptions.AccountNotFoundException;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.CannotCancelOrderException;
import com.novatech.cybertech.exceptions.CannotRemoveItemFromEmptyCartException;
import com.novatech.cybertech.exceptions.CartIsEmptyException;
import com.novatech.cybertech.exceptions.CartItemNotFoundException;
import com.novatech.cybertech.exceptions.CartNotFoundException;
import com.novatech.cybertech.exceptions.CommentPostNotAllowedException;
import com.novatech.cybertech.exceptions.DiscountTypeCannotBeNullForStrategy;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.exceptions.FailedRetryingPayment;
import com.novatech.cybertech.exceptions.NegativeQuantityException;
import com.novatech.cybertech.exceptions.NoDefaultBankCartSetException;
import com.novatech.cybertech.exceptions.NoPreviousPaymentAttemptException;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.exceptions.OrderAlreadyShippedException;
import com.novatech.cybertech.exceptions.OrderDoesntBelongsToUserException;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.exceptions.OrderSummuryReportJobFailedException;
import com.novatech.cybertech.exceptions.PaymentAlreadyCompletedForThisOrderException;
import com.novatech.cybertech.exceptions.PaymentFailedException;
import com.novatech.cybertech.exceptions.PaymentNotFoundException;
import com.novatech.cybertech.exceptions.PaymentProcessingException;
import com.novatech.cybertech.exceptions.ProductAlreadyInWishlist;
import com.novatech.cybertech.exceptions.ProductConstraintsViolationException;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.ReviewNotFoundException;
import com.novatech.cybertech.exceptions.UnauthorizedBankCardAccessException;
import com.novatech.cybertech.exceptions.UserAlreadyExistsException;
import com.novatech.cybertech.exceptions.UserNotActiveException;
import com.novatech.cybertech.exceptions.UserNotAuthorOfReviewException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.exceptions.WishlistNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustive per-handler test of {@link ErrorManagementController}. Builds one test per
 * {@code @ExceptionHandler} method and asserts the returned {@link ResponseEntity} status,
 * envelope body, and the load-bearing fields (httpStatusCode, errorCodeType, message).
 *
 * Pinned bugs (carried over from SA4.5R wave — DO NOT renumber):
 *   - BUG-138: handleMethodArgumentNotValidException returns canned message; bind-errors dropped.
 *   - BUG-139: handleUnrecognizedPropertyException — F2 wave fixed (now returns ErrorResponseDto).
 *   - BUG-140: catch-all leaks ex.getMessage() into the 500 body.
 *
 * F2-wave fix verification (per progress.md 2026-04-23T10:30Z):
 *   - BUG-2503 (HttpMessageNotReadableException → 400): handler exists. CONFIRMED FIXED.
 *   - BUG-029 (MethodArgumentTypeMismatchException → 400): handler exists. CONFIRMED FIXED.
 *   - BUG-031 (AccessDeniedException + AuthorizationDeniedException → 403): combined handler exists. CONFIRMED FIXED.
 *   - BUG-139 (UnrecognizedProperty returns ErrorResponseDto, not String): CONFIRMED FIXED.
 *   - BUG-138 FIXED — handler now surfaces field-level validation errors; tests re-enabled.
 *   - BUG-140 FIXED — catch-all handler returns a generic message and never leaks ex.getMessage().
 */
class ErrorManagementControllerBranchTest {

    private final ErrorManagementController controller = new ErrorManagementController();

    // --- Domain exceptions: passthrough ex.getMessage(), 4xx, FUNCTIONAL --------

    @Test
    @DisplayName("AccountNotFoundException → 404 FUNCTIONAL with passthrough message")
    void accountNotFoundExceptionReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleAccountNotFoundException(new AccountNotFoundException("no such account"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "no such account");
    }

    @Test
    @DisplayName("UnauthorizedBankCardAccessException → 403 FUNCTIONAL with passthrough message")
    void unauthorizedBankCardAccessReturns403() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleUnauthorizedBankCardAccessException(new UnauthorizedBankCardAccessException("not your card"));

        assertEnvelope(response, HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL, "not your card");
    }

    @Test
    @DisplayName("CannotRemoveItemFromEmptyCartException → 403 FUNCTIONAL canned message")
    void cannotRemoveItemFromEmptyCartReturns403() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleCannotRemoveItemFromEmptyCartException(new CannotRemoveItemFromEmptyCartException("ignored"));

        assertEnvelope(response, HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL, "Cannot remove item from empty cart.");
    }

    @Test
    @DisplayName("ProductConstraintsViolationException → 400 INVALID_REQUEST canned message")
    void productConstraintsViolationReturns400() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleProductConstraintsViolationException(new ProductConstraintsViolationException("ignored"));

        assertEnvelope(response, HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL, "constraints");
    }

    @Test
    @DisplayName("CannotCancelOrderException → 409 FUNCTIONAL with passthrough message")
    void cannotCancelOrderReturns409() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleCannotCancelOrderException(new CannotCancelOrderException("already shipped"));

        assertEnvelope(response, HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL, "already shipped");
    }

    @Test
    @DisplayName("ProductNotFoundException → 404 FUNCTIONAL with passthrough message")
    void productNotFoundReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleProductNotFoundException(new ProductNotFoundException("missing"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "missing");
    }

    @Test
    @DisplayName("OrderDoesntBelongsToUserException → 403 FUNCTIONAL with passthrough message")
    void orderDoesntBelongToUserReturns403() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleOrderDoesntBelongsToUserException(new OrderDoesntBelongsToUserException("not yours"));

        assertEnvelope(response, HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL, "not yours");
    }

    @Test
    @DisplayName("FailedRetryingPayment → 422 FUNCTIONAL with passthrough message")
    void failedRetryingPaymentReturns422() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleFailedUpdatingOrder(new FailedRetryingPayment("retry boom"));

        assertEnvelope(response, HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodeType.FUNCTIONAL, "retry boom");
    }

    @Test
    @DisplayName("CartNotFoundException → 404 FUNCTIONAL with passthrough message")
    void cartNotFoundReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleCartNotFoundException(new CartNotFoundException("no cart"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "no cart");
    }

    @Test
    @DisplayName("OrderAlreadyShippedException → 409 FUNCTIONAL with passthrough message")
    void orderAlreadyShippedReturns409() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleOrderAlreadyShippedException(new OrderAlreadyShippedException("shipped"));

        assertEnvelope(response, HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL, "shipped");
    }

    @Test
    @DisplayName("NoPreviousPaymentAttemptException → 404 FUNCTIONAL with passthrough message")
    void noPreviousPaymentAttemptReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleNoPreviousPaymentAttemptException(new NoPreviousPaymentAttemptException("none"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "none");
    }

    @Test
    @DisplayName("ProductAlreadyInWishlist → 409 FUNCTIONAL with passthrough message")
    void productAlreadyInWishlistReturns409() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleProductAlreadyInWishlist(new ProductAlreadyInWishlist("dupe"));

        assertEnvelope(response, HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL, "dupe");
    }

    @Test
    @DisplayName("WishlistNotFoundException → 404 FUNCTIONAL with passthrough message")
    void wishlistNotFoundReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleWishlistNotFoundException(new WishlistNotFoundException("no wl"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "no wl");
    }

    @Test
    @DisplayName("CartItemNotFoundException → 404 FUNCTIONAL with passthrough message")
    void cartItemNotFoundReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleCartItemNotFoundException(new CartItemNotFoundException("missing"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "missing");
    }

    @Test
    @DisplayName("CartIsEmptyException → 403 FUNCTIONAL with passthrough message")
    void cartIsEmptyReturns403() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleCartIsEmptyException(new CartIsEmptyException("empty"));

        assertEnvelope(response, HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL, "empty");
    }

    @Test
    @DisplayName("AccessTokenRetrievalException → 500 TECHNICAL with passthrough message")
    void accessTokenRetrievalReturns500() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleAccessTokenRetrievalException(new AccessTokenRetrievalException("kc down"));

        assertEnvelope(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL, "kc down");
    }

    @Test
    @DisplayName("BankCardExpiredException → 400 FUNCTIONAL with passthrough message")
    void bankCardExpiredReturns400() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleBankCardExpiredException(new BankCardExpiredException("expired"));

        assertEnvelope(response, HttpStatus.BAD_REQUEST, ErrorCodeType.FUNCTIONAL, "expired");
    }

    @Test
    @DisplayName("BankCardNotFoundException → 404 FUNCTIONAL with passthrough message")
    void bankCardNotFoundReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleBankCardNotFoundException(new BankCardNotFoundException("missing card"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "missing card");
    }

    @Test
    @DisplayName("CommentPostNotAllowedException → 403 FUNCTIONAL with passthrough message")
    void commentPostNotAllowedReturns403() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleCommentPostNotAllowedException(new CommentPostNotAllowedException("no"));

        assertEnvelope(response, HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL, "no");
    }

    @Test
    @DisplayName("IdempotencyKeyGenerationException → 500 TECHNICAL with passthrough message")
    void idempotencyKeyGenerationReturns500() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleIdempotencyKeyGenerationException(
                        new com.novatech.cybertech.exceptions.IdempotencyKeyGenerationException(
                                "no-algo", new java.security.NoSuchAlgorithmException("x")));

        assertEnvelope(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL, "no-algo");
    }

    @Test
    @DisplayName("NoDefaultBankCartSetException → 403 FUNCTIONAL with passthrough message")
    void noDefaultBankCartSetReturns403() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleNoDefaultBankCartSetException(new NoDefaultBankCartSetException("no default"));

        assertEnvelope(response, HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL, "no default");
    }

    @Test
    @DisplayName("NoStrategyFoundForProcessingTheRequest → 500 TECHNICAL with passthrough message")
    void noStrategyFoundReturns500() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleNoStrategyFoundForProcessingTheRequest(new NoStrategyFoundForProcessingTheRequest("no strategy"));

        assertEnvelope(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL, "no strategy");
    }

    @Test
    @DisplayName("NotEnoughStockException → 409 FUNCTIONAL with passthrough message")
    void notEnoughStockReturns409() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleNotEnoughStockException(new NotEnoughStockException("oos"));

        assertEnvelope(response, HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL, "oos");
    }

    @Test
    @DisplayName("OrderNotFoundException → 404 FUNCTIONAL with passthrough message")
    void orderNotFoundReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleOrderNotFoundException(new OrderNotFoundException("no order"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "no order");
    }

    @Test
    @DisplayName("OrderSummuryReportJobFailedException → 500 TECHNICAL with passthrough message")
    void orderSummaryReportJobFailedReturns500() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleOrderSummaryReportJobFailedException(new OrderSummuryReportJobFailedException("job fail"));

        assertEnvelope(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL, "job fail");
    }

    @Test
    @DisplayName("PaymentAlreadyCompletedForThisOrderException → 409 FUNCTIONAL with passthrough message")
    void paymentAlreadyCompletedReturns409() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handlePaymentAlreadyCompletedException(new PaymentAlreadyCompletedForThisOrderException("done"));

        assertEnvelope(response, HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL, "done");
    }

    @Test
    @DisplayName("PaymentFailedException → 402 FUNCTIONAL with passthrough message")
    void paymentFailedReturns402() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handlePaymentFailedException(new PaymentFailedException("declined"));

        assertEnvelope(response, HttpStatus.PAYMENT_REQUIRED, ErrorCodeType.FUNCTIONAL, "declined");
    }

    @Test
    @DisplayName("PaymentNotFoundException → 404 FUNCTIONAL with passthrough message")
    void paymentNotFoundReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handlePaymentNotFoundException(new PaymentNotFoundException("missing"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "missing");
    }

    @Test
    @DisplayName("PaymentProcessingException → 500 TECHNICAL with passthrough message")
    void paymentProcessingReturns500() {
        // PaymentProcessingException requires a StripeException; subclass it cheaply for the test.
        final com.stripe.exception.StripeException stripeEx = new com.stripe.exception.ApiException("boom", null, null, 0, null);
        final ResponseEntity<ErrorResponseDto> response =
                controller.handlePaymentProcessingException(new PaymentProcessingException("processing fail", stripeEx));

        assertEnvelope(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL, "processing fail");
    }

    @Test
    @DisplayName("UserAlreadyExistsException → 409 FUNCTIONAL with passthrough message")
    void userAlreadyExistsReturns409() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleUserAlreadyExistsException(new UserAlreadyExistsException("dupe user"));

        assertEnvelope(response, HttpStatus.CONFLICT, ErrorCodeType.FUNCTIONAL, "dupe user");
    }

    @Test
    @DisplayName("UserNotActiveException → 403 FUNCTIONAL with passthrough message")
    void userNotActiveReturns403() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleUserNotActiveException(new UserNotActiveException("inactive"));

        assertEnvelope(response, HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL, "inactive");
    }

    @Test
    @DisplayName("DiscountTypeNotActiveException → 400 FUNCTIONAL with passthrough message")
    void discountTypeNotActiveReturns400() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleDiscountTypeNotActiveException(new DiscountTypeNotActiveException("not active"));

        assertEnvelope(response, HttpStatus.BAD_REQUEST, ErrorCodeType.FUNCTIONAL, "not active");
    }

    @Test
    @DisplayName("DiscountTypeCannotBeNullForStrategy → 500 TECHNICAL with passthrough message")
    void discountTypeCannotBeNullReturns500() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleDiscountTypeCannotBeNullForStrategy(new DiscountTypeCannotBeNullForStrategy("null type"));

        assertEnvelope(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL, "null type");
    }

    @Test
    @DisplayName("NegativeQuantityException → 400 TECHNICAL with passthrough message")
    void negativeQuantityReturns400() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleNegativeQuantityException(new NegativeQuantityException("qty < 1"));

        assertEnvelope(response, HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL, "qty < 1");
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 TECHNICAL with passthrough message")
    void illegalArgumentReturns400() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleIllegalArgumentException(new IllegalArgumentException("bad arg"));

        assertEnvelope(response, HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL, "bad arg");
    }

    @Test
    @DisplayName("ReviewNotFoundException → 404 FUNCTIONAL with passthrough message")
    void reviewNotFoundReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleReviewNotFoundException(new ReviewNotFoundException("no review"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "no review");
    }

    @Test
    @DisplayName("UserNotFoundException → 404 FUNCTIONAL with passthrough message")
    void userNotFoundReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleUserNotFoundException(new UserNotFoundException("no user"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.FUNCTIONAL, "no user");
    }

    @Test
    @DisplayName("UserNotAuthorOfReviewException → 403 FUNCTIONAL with passthrough message")
    void userNotAuthorOfReviewReturns403() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleUserNotAuthorOfReviewException(new UserNotAuthorOfReviewException("not author"));

        assertEnvelope(response, HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL, "not author");
    }

    @Test
    @DisplayName("NotificationDeliveryException → 500 TECHNICAL with passthrough message")
    void notificationDeliveryReturns500() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleNotificationDeliveryException(new NotificationDeliveryException("smtp down"));

        assertEnvelope(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL, "smtp down");
    }

    @Test
    @DisplayName("NoResourceFoundException → 404 TECHNICAL canned message")
    void noResourceFoundReturns404() {
        final ResponseEntity<ErrorResponseDto> response =
                controller.handleNoResourceFoundException(
                        new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/missing", "/missing"));

        assertEnvelope(response, HttpStatus.NOT_FOUND, ErrorCodeType.TECHNICAL, "doesn't exists");
    }

    // --- BUG-2503: HttpMessageNotReadableException ----------------------------

    @Nested
    @DisplayName("HttpMessageNotReadableException — BUG-2503 (F2 wave fixed)")
    class HttpMessageNotReadable {

        @Test
        @DisplayName("→ 400 MALFORMED_JSON canned message (F2 fix verified)")
        void httpMessageNotReadableReturns400() {
            final HttpInputMessage emptyInput = new HttpInputMessage() {
                @Override
                public InputStream getBody() {
                    return new ByteArrayInputStream(new byte[0]);
                }

                @Override
                public org.springframework.http.HttpHeaders getHeaders() {
                    return new org.springframework.http.HttpHeaders();
                }
            };
            final HttpMessageNotReadableException ex = new HttpMessageNotReadableException("malformed", emptyInput);

            final ResponseEntity<ErrorResponseDto> response = controller.handleHttpMessageNotReadableException(ex);

            assertEnvelope(response, HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL, "Malformed JSON");
        }
    }

    // --- BUG-029: MethodArgumentTypeMismatchException -------------------------

    @Nested
    @DisplayName("MethodArgumentTypeMismatchException — BUG-029 (F2 wave fixed)")
    class MethodArgumentTypeMismatch {

        @Test
        @DisplayName("→ 400 METHOD_ARGUMENT_TYPE_MISMATCH naming the offending parameter (F2 fix verified)")
        void methodArgumentTypeMismatchReturns400() {
            final MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                    "not-a-uuid", java.util.UUID.class, "id", null, new IllegalArgumentException("nope"));

            final ResponseEntity<ErrorResponseDto> response = controller.handleMethodArgumentTypeMismatchException(ex);

            assertEnvelope(response, HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL, "id");
            assertThat(response.getBody().getMessage()).contains("Invalid value for parameter");
        }
    }

    // --- BUG-031: AccessDeniedException + AuthorizationDeniedException --------

    @Nested
    @DisplayName("AccessDenied / AuthorizationDenied — BUG-031 (F2 wave fixed)")
    class AccessDeniedHandling {

        @Test
        @DisplayName("AccessDeniedException → 403 ACCESS_DENIED canned message (F2 fix verified)")
        void accessDeniedReturns403() {
            final ResponseEntity<ErrorResponseDto> response =
                    controller.handleAuthorizationDeniedException(new AccessDeniedException("denied"));

            assertEnvelope(response, HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL, "Access denied");
        }

        @Test
        @DisplayName("AuthorizationDeniedException → 403 ACCESS_DENIED canned message (F2 fix verified)")
        void authorizationDeniedReturns403() {
            final org.springframework.security.authorization.AuthorizationResult result =
                    new org.springframework.security.authorization.AuthorizationDecision(false);
            final ResponseEntity<ErrorResponseDto> response =
                    controller.handleAuthorizationDeniedException(new AuthorizationDeniedException("not allowed", result));

            assertEnvelope(response, HttpStatus.FORBIDDEN, ErrorCodeType.FUNCTIONAL, "Access denied");
        }
    }

    // --- BUG-138: handleMethodArgumentNotValidException ------------------------

    @Nested
    @DisplayName("MethodArgumentNotValidException — BUG-138 (fixed)")
    class MethodArgumentNotValid {

        @Test
        @DisplayName("FIX BUG-138: field errors are surfaced in the response message")
        void fieldErrorsAreSurfacedInMessage() throws Exception {
            final MethodArgumentNotValidException ex = buildMethodArgumentNotValidExceptionWithFieldErrors();

            final ResponseEntity<ErrorResponseDto> response =
                    controller.handleMethodArgumentNotValidException(ex);

            assertEnvelope(response, HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL, "Validation failed:");
            assertThat(response.getBody().getMessage()).contains("name (name must not be blank)");
            assertThat(response.getBody().getMessage()).contains("email (email must be a valid address)");
        }

        @Test
        @DisplayName("FIX BUG-138: fallback to canned message when no field errors present")
        void fallbackToCannedMessageWhenNoFieldErrors() throws Exception {
            final BindingResult emptyResult = new BeanPropertyBindingResult(new SyntheticTarget(), "syntheticTarget");
            final Method m = ErrorManagementControllerBranchTest.class.getDeclaredMethod("syntheticMethodForMethodParameter", String.class);
            final MethodParameter methodParameter = new MethodParameter(m, 0);
            final MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, emptyResult);

            final ResponseEntity<ErrorResponseDto> response =
                    controller.handleMethodArgumentNotValidException(ex);

            assertEnvelope(response, HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL, "Invalid Request or Request Poorly Constructed");
        }

        private MethodArgumentNotValidException buildMethodArgumentNotValidExceptionWithFieldErrors() throws Exception {
            final BindingResult bindingResult = new BeanPropertyBindingResult(new SyntheticTarget(), "syntheticTarget");
            bindingResult.rejectValue("name", "NotBlank", "name must not be blank");
            bindingResult.rejectValue("email", "Email", "email must be a valid address");

            // MethodParameter requires a real Method; pick any method with a parameter.
            final Method m = ErrorManagementControllerBranchTest.class.getDeclaredMethod("syntheticMethodForMethodParameter", String.class);
            final MethodParameter methodParameter = new MethodParameter(m, 0);

            return new MethodArgumentNotValidException(methodParameter, bindingResult);
        }
    }

    // --- BUG-139: handleUnrecognizedPropertyException --------------------------

    @Nested
    @DisplayName("UnrecognizedPropertyException — BUG-139 (F2 wave fixed)")
    class UnrecognizedProperty {

        @Test
        @DisplayName("→ 400 ErrorResponseDto with property name in message (F2 fix verified)")
        void unrecognizedPropertyReturnsErrorResponseDto() throws Exception {
            final UnrecognizedPropertyException ex = buildUnrecognizedPropertyException();

            final ResponseEntity<ErrorResponseDto> response = controller.handleUnrecognizedPropertyException(ex);

            assertEnvelope(response, HttpStatus.BAD_REQUEST, ErrorCodeType.TECHNICAL, "unknownProperty");
            assertThat(response.getBody()).isInstanceOf(ErrorResponseDto.class);
            assertThat(response.getBody().getMessage()).contains("Unknown property");
        }

        private UnrecognizedPropertyException buildUnrecognizedPropertyException() {
            try {
                final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.readValue("{\"unknownProperty\":42}", SyntheticTarget.class);
                throw new IllegalStateException("expected UnrecognizedPropertyException to be thrown");
            } catch (UnrecognizedPropertyException upe) {
                return upe;
            } catch (IOException unexpected) {
                throw new IllegalStateException("unexpected IOException while building UnrecognizedPropertyException", unexpected);
            }
        }
    }

    // --- BUG-140: catch-all RuntimeException leaks ex.getMessage() ------------

    @Nested
    @DisplayName("RuntimeException catch-all — BUG-140 (fixed)")
    class CatchAll {

        @Test
        @DisplayName("FIX BUG-140: catch-all returns generic message, never leaks exception text")
        void catchAllShouldNotLeakExceptionMessage() {
            final String sensitive = "sk_live_DEADBEEF_secret";
            final ResponseEntity<ErrorResponseDto> response =
                    controller.handleRuntimeException(new RuntimeException(sensitive));

            assertEnvelope(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodeType.TECHNICAL, "unexpected error");
            assertThat(response.getBody().getMessage()).doesNotContain(sensitive);
            assertThat(response.getBody().getMessage())
                    .isEqualTo("An unexpected error occurred. Please contact support if the issue persists.");
        }

        @Test
        @DisplayName("FIX BUG-140: SQL-fragment style messages also scrubbed")
        void catchAllScrubsSqlFragments() {
            final String sqlFragment = "could not extract column [user_password_hash]";
            final ResponseEntity<ErrorResponseDto> response =
                    controller.handleRuntimeException(new RuntimeException(sqlFragment));

            assertThat(response.getBody().getMessage()).doesNotContain(sqlFragment);
            assertThat(response.getBody().getMessage()).doesNotContain("user_password_hash");
        }

        @Test
        @DisplayName("catch-all returns 500 APPLICATION_ERROR even when message is null")
        void catchAllHandlesNullMessage() {
            final ResponseEntity<ErrorResponseDto> response =
                    controller.handleRuntimeException(new RuntimeException());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getErrorCodeType()).isEqualTo(ErrorCodeType.TECHNICAL);
        }
    }

    // --- shared helpers --------------------------------------------------------

    private static void assertEnvelope(final ResponseEntity<ErrorResponseDto> response,
                                       final HttpStatus expectedStatus,
                                       final ErrorCodeType expectedType,
                                       final String containsMessage) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        final ErrorResponseDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getHttpStatusCode()).isEqualTo(expectedStatus.value());
        assertThat(body.getErrorCodeType()).isEqualTo(expectedType);
        if (containsMessage != null) {
            assertThat(body.getMessage()).contains(containsMessage);
        }
    }

    /** Used as the {@code MethodParameter}-bearing target for the BUG-138 binding-result builder. */
    @SuppressWarnings("unused")
    private void syntheticMethodForMethodParameter(final String unused) {
        // Reflection target only — never invoked.
    }

    /** Plain POJO used by the BindingResult and UnrecognizedPropertyException builders. */
    public static class SyntheticTarget {
        private String name;
        private String email;

        public String getName() { return name; }
        public void setName(final String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(final String email) { this.email = email; }
    }
}
