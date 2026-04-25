package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.dto.response.order.OrderStatusDto;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Public contract for order-management operations exposed to the user-facing controllers.
 *
 * <p>Implementations own validation, stock reservation, payment delegation, and event
 * publication; persistence is delegated to the order/cart repositories.</p>
 *
 * <p><b>JWT contract (BUG-054):</b> every method that takes a {@link Jwt} requires a
 * non-null {@code jwt.getSubject()}. A missing subject must surface as
 * {@link com.novatech.cybertech.exceptions.UserNotFoundException} (HTTP 404 via the advice)
 * rather than NPE or a generic 500.</p>
 *
 * <p><b>Discount contract (BUG-052):</b> {@link #retryPayment} reuses the order's already-
 * discounted {@code totalAmount} as-is; implementations must NOT re-apply any discount
 * strategy on retry — the {@code totalAmount} is set once at {@link #placeOrder} time and
 * already reflects every applicable discount.</p>
 */
public interface OrderManagementService {

    /**
     * Convert the authenticated user's cart into a persisted order, reserve stock, attempt
     * payment, and publish an {@code OrderCreatedEvent}.
     *
     * @param orderPlacingRequestDto validated payload (shipping address, payment type, etc.).
     * @param jwt                    caller identity — {@code sub} claim must be non-null.
     * @return the freshly persisted order mapped to {@link OrderResponseDto}.
     */
    OrderResponseDto placeOrder(final OrderPlacingRequestDto orderPlacingRequestDto, final Jwt jwt);

    /**
     * Soft-cancel an existing order (status flip to {@code CANCELED} + refunds for prior
     * successful PAYMENT attempts). Differs from {@link #deleteByUUID} which hard-deletes
     * the row and releases stock.
     */
    OrderResponseDto cancelOrder(final UUID orderUUID, final Jwt jwt);

    /**
     * Hard-delete an order — release any stock reservation and remove the row. No refund is
     * issued; use {@link #cancelOrder} when a refund is required.
     */
    void deleteByUUID(final UUID uuid, final Jwt jwt);

    /**
     * Mid-flight order update: overwrite items + shipping fields, then charge / refund the
     * delta or just commit the existing reservation when the new total matches what's
     * already paid.
     */
    OrderResponseDto updateOrder(final OrderUpdateRequestDto orderUpdateRequestDto, final Jwt jwt);

    /**
     * Retry a failed (or pending) payment using the order's already-discounted
     * {@code totalAmount} verbatim — see the BUG-052 contract on the type-level Javadoc.
     */
    OrderResponseDto retryPayment(final UUID orderUuid, final Jwt jwt);

    /**
     * Lightweight status read for the order-confirmation polling loop. Verifies the caller
     * owns the order before returning. Use {@link #getByUUID(UUID)} (admin path) or the
     * full {@code OrderResponseDto} read for richer payloads.
     */
    OrderStatusDto getStatusByUUID(final UUID orderUuid, final String keycloakId);
}
