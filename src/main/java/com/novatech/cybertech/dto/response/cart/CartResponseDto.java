package com.novatech.cybertech.dto.response.cart;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
//@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public class CartResponseDto {
    private UUID cartUuid;
    private UUID userUuid;
    private List<CartItemResponseDto> items;
    private BigDecimal totalPrice;
}