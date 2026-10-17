package com.novatech.cybertech.dto.response.shipping;

import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

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

    @Schema(description = "Indicative earliest delivery, in business days (Monday to Friday, holidays ignored) from the day the order is handled.", example = "1")
    private int minDeliveryBusinessDays;

    @Schema(description = "Indicative latest delivery, in business days.", example = "2")
    private int maxDeliveryBusinessDays;

    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "Local time (timeZone) after which an order is handled the next business day; null when there is none.", type = "string", example = "15:00", nullable = true)
    private LocalTime cutoffTime;

    @Schema(description = "IANA time zone of cutoffTime.", example = "Europe/Paris")
    private String timeZone;
}
