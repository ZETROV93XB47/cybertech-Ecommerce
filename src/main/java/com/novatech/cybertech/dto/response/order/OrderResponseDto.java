package com.novatech.cybertech.dto.response.order;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * An order as returned by the API, with the breakdown of its price.
 *
 * <p>Invariant on the breakdown: {@code totalAmount = max(0, subtotalAmount - discountAmount) +
 * shippingCost}. All amounts are in {@link #currency}.
 *
 * <p>Orders placed before the breakdown was persisted return {@code subtotalAmount},
 * {@code discountAmount}, {@code shippingCost} and {@code discountCalculationType} as
 * {@code null}; {@code totalAmount} is always present.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponseDto {

    private UUID uuid;
    private UUID userUuid;

    private LocalDate orderDate;

    @Schema(description = "When the order was placed (date and time of orderDate).")
    private LocalDateTime createdAt;

    private OrderStatus status;

    @Schema(description = "Amount charged: max(0, subtotalAmount - discountAmount) + shippingCost.")
    private BigDecimal totalAmount;
    private BigDecimal refundedAmount;

    @Schema(description = "ISO 4217 code of every amount of this order.", example = "EUR")
    private String currency;

    @Schema(description = "Sum of the order lines before discount and shipping. Null on orders placed before this field existed.", types = {"number", "null"})
    private BigDecimal subtotalAmount;

    @Schema(description = "Campaign the order was placed with, NO_DISCOUNT if none.", example = "BLACK_FRIDAY")
    private String discountKey;

    @Schema(description = "Calculation type of the campaign at purchase time (NONE for NO_DISCOUNT). Null on orders placed before this field existed.", types = {"string", "null"})
    private DiscountCalculationType discountCalculationType;

    @Schema(description = "Discount granted on subtotalAmount; 0 when the campaign minimum was not reached. Null on orders placed before this field existed.", types = {"number", "null"})
    private BigDecimal discountAmount;

    private ShippingType shippingType;
    private ShippingProvider shippingProvider;

    @Schema(description = "Shipping fee for shippingProvider / shippingType. Null on orders placed before this field existed.", types = {"number", "null"})
    private BigDecimal shippingCost;

    private String shippingAddress;

    @Schema(description = "Indicative earliest delivery day (ISO date, business days, holidays ignored); null on orders placed before estimates existed.", example = "2026-10-15", nullable = true)
    private LocalDate estimatedDeliveryFrom;

    @Schema(description = "Indicative latest delivery day (ISO date); null on orders placed before estimates existed.", example = "2026-10-16", nullable = true)
    private LocalDate estimatedDeliveryTo;

    private List<OrderItemResponseDto> orderItems;
}
