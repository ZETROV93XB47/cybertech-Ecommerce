package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.entities.OrderEntity;

/**
 * Transactional inner half of {@code OrderManagementServiceImp#placeOrder}.
 *
 * <h2>Why this is a separate bean</h2>
 * {@code placeOrder} used to build the order, reserve stock, AND attempt the Stripe payment
 * inside one single {@code @Transactional} method. That is correct for a business decline (Stripe
 * returns a normal {@code requires_payment_method} result, no exception) — the order persists in
 * {@code AWAITING_PAYMENT}, retryable via {@code retryPayment}. But a Stripe
 * <em>infrastructure</em> failure (network outage past the {@code @Retry} budget, circuit breaker
 * {@code OPEN}) makes {@code StripePaymentAttemptProcessor} throw {@code PaymentProcessingException}
 * instead of returning a result — and because {@code PaymentServiceImp.processPayment} is itself
 * {@code @Transactional} and was PARTICIPATING in {@code placeOrder}'s already-open transaction,
 * Spring's transactional interceptor marks that shared transaction {@code rollbackOnly} the moment
 * the exception propagates out of it — before {@code placeOrder} ever gets a chance to catch it.
 * Catching the exception locally in {@code placeOrder} does not help: the transaction is already
 * poisoned and would throw {@code UnexpectedRollbackException} at commit time regardless, wiping
 * the order that had just been created — a strictly worse outcome than a business decline.
 *
 * <p>The fix is the same two-bean split used elsewhere in this codebase
 * (see {@code OrderCancellationTransactionalDelegate}, {@code CartWriteTransactionalDelegate}):
 * order creation + stock reservation commit in THEIR OWN transaction, on this separate bean, before
 * {@code placeOrder} ever calls {@code PaymentService#processPayment}. By the time the payment
 * attempt runs, the order is already durably committed — a subsequent
 * {@code PaymentProcessingException} can only roll back the (separate) payment-attempt transaction,
 * never the order itself. {@code placeOrder} degrades a caught {@code PaymentProcessingException}
 * to the same {@code AWAITING_PAYMENT} + released-stock state a business decline already leaves.
 */
public interface OrderCreationTransactionalDelegate {

    /**
     * Validates the caller's cart and payment eligibility, prices the order, persists it, and
     * reserves stock for every line — all inside its own committed transaction.
     *
     * @param req        the placement request (shipping, discount, payment type).
     * @param keycloakId caller's Keycloak subject, used to resolve the user and their cart.
     * @return the persisted order, in {@link com.novatech.cybertech.entities.enums.OrderStatus#AWAITING_PAYMENT},
     *         with stock already reserved.
     * @throws com.novatech.cybertech.exceptions.UserNotFoundException   when no user matches {@code keycloakId}.
     * @throws com.novatech.cybertech.exceptions.CartNotFoundException   when the user's cart is missing or empty.
     * @throws com.novatech.cybertech.exceptions.BankCardNotFoundException when the user has no bank card on file.
     * @throws com.novatech.cybertech.exceptions.NotEnoughStockException when any cart line is no longer available.
     */
    OrderEntity createAndReserveStock(OrderPlacingRequestDto req, String keycloakId);
}
