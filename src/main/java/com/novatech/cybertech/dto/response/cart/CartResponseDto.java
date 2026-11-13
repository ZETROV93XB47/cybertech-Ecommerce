package com.novatech.cybertech.dto.response.cart;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponseDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private UUID cartUuid;
    private UUID userUuid;
    private List<CartItemResponseDto> items;
    private BigDecimal totalPrice;
}