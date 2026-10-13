package com.novatech.cybertech.dto.response.shipping;

import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One available (carrier, shipping type) pair and the fee an order placed with it is charged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingOptionResponseDto {

    private ShippingProvider provider;

    private ShippingType type;

    @Schema(description = "Shipping fee, exactly the shippingCost an order placed with this provider and type is charged.", example = "15.00")
    private BigDecimal cost;

    @Schema(description = "ISO 4217 code of cost.", example = "EUR")
    private String currency;
}
