package com.novatech.cybertech.dto.response.cart;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponseDto {
    private UUID cartItemUuid;
    private UUID productUuid;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineItemTotalPrice;
}