package com.novatech.cybertech.dto.response.order;

import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculationResultDto {

    private BigDecimal baseAmount;

    private BigDecimal discountAmount;

    private BigDecimal finalAmount;

    private CurrencyCode currencyCode;

    private DiscountType discountType;

    public Money asFinalMoney() {
        return new Money(finalAmount, currencyCode);
    }
}
