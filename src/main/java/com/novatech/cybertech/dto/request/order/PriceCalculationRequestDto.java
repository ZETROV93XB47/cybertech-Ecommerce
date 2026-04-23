package com.novatech.cybertech.dto.request.order;

import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class PriceCalculationRequestDto {

    @NotEmpty
    @Valid
    private List<OrderItemPriceDto> items;

    @NotNull
    private DiscountType discountType;

    @NotNull
    private CurrencyCode currencyCode;
}
