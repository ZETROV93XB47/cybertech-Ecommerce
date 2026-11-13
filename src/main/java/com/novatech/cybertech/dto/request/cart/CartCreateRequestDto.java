package com.novatech.cybertech.dto.request.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartCreateRequestDto {
    @Valid
    @NotNull(message = "Cart items cannot be null")
    private List<CartItemAddRequestDto> cartItemAddRequestDtos;
}