package com.novatech.cybertech.dto.request.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class OrderItemPriceDto {

    @NotNull
    private UUID productUuid;

    @NotNull
    private BigDecimal unitPrice;

    @NotNull
    @Min(1)
    private Integer quantity;
}
