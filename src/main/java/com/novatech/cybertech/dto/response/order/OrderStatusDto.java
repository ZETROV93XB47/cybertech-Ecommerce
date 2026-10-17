package com.novatech.cybertech.dto.response.order;

import com.novatech.cybertech.entities.enums.OrderStatus;
import lombok.Builder;

import java.util.UUID;

/**
 * Lightweight response for {@code GET /api/v1/services/management/order/status/{uuid}} —
 * carries just the order's current {@link OrderStatus}, intended for the order-confirmation
 * page polling loop after a Stripe payment so the frontend doesn't refetch the full order.
 */
@Builder
public record OrderStatusDto(
        UUID uuid,
        OrderStatus status
) {
}
