package com.novatech.cybertech.dto.response.cart;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
//@EqualsAndHashCode(callSuper = true)
public class CartItemResponseDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private UUID cartItemUuid;
    private UUID productUuid;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineItemTotalPrice;
}