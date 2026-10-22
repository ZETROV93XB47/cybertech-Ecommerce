package com.novatech.cybertech.dto.response.order;


import io.swagger.v3.oas.annotations.media.Schema;
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
public class OrderItemResponseDto {

    private UUID orderItemUuid;
    private UUID productUuid;
    private String productName;
    @Schema(description = "Category key of the product as it is now (not frozen at purchase time); null when the product no longer exists.", types = {"string", "null"}, example = "SMARTPHONE")
    private String category;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineItemTotalPrice;
}