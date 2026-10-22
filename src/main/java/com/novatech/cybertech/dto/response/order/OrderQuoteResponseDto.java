package com.novatech.cybertech.dto.response.order;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Price an order placed now from the caller's cart would have, with the same breakdown as
 * {@link OrderResponseDto}: {@code totalAmount = max(0, subtotalAmount - discountAmount) +
 * shippingCost}, all in {@link #currency}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderQuoteResponseDto {

    @Schema(description = "Sum of the cart lines at the products' current prices, before discount and shipping.")
    private BigDecimal subtotalAmount;

    @Schema(description = "Campaign the quote was computed with, NO_DISCOUNT if none.", example = "BLACK_FRIDAY")
    private String discountKey;

    @Schema(description = "Calculation type of the campaign (NONE for NO_DISCOUNT).")
    private DiscountCalculationType discountCalculationType;

    @Schema(description = "Discount granted on subtotalAmount; 0 when discountApplied is false.")
    private BigDecimal discountAmount;

    @Schema(description = "False when there is no campaign or when subtotalAmount is below minOrderAmount.")
    private boolean discountApplied;

    @Schema(description = "Minimum subtotal the campaign requires; null when it has none.", types = {"number", "null"})
    private BigDecimal minOrderAmount;

    private BigDecimal shippingCost;

    @Schema(description = "max(0, subtotalAmount - discountAmount) + shippingCost — what placing the order now would charge.")
    private BigDecimal totalAmount;

    @Schema(description = "ISO 4217 code of every amount.", example = "EUR")
    private String currency;
}
