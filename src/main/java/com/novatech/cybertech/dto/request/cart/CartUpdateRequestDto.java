package com.novatech.cybertech.dto.request.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BUG-026 — Proper update DTO for the cart resource.
 * <p>
 * Carries the changeable fields of a cart (currently the list of items to add /
 * replace). Replaces the previous misuse of {@code CartItemRemoveRequestDto} as
 * the update payload, which was semantically wrong because that DTO carries a
 * single product UUID + a quantity to remove and cannot describe a bulk update.
 *
 * <p>Nested {@code @Valid} ensures each {@link CartItemAddRequestDto} respects its
 * own constraints ({@code @Min(1)} on quantity).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartUpdateRequestDto {

    @Valid
    @NotNull(message = "Cart items cannot be null")
    private List<CartItemAddRequestDto> cartItemAddRequestDtos;
}
