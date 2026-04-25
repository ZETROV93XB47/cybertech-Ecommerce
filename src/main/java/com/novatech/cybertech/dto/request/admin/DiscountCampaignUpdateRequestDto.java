package com.novatech.cybertech.dto.request.admin;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PATCH payload for the admin discount endpoint. Every field is optional —
 * {@code null} means "do not change". Bounds are validated when the field is supplied.
 */
public record DiscountCampaignUpdateRequestDto(
        Boolean enabled,
        DiscountCalculationType calculationType,
        @DecimalMin(value = "0.00") @DecimalMax(value = "100.00") BigDecimal percentage,
        @PositiveOrZero BigDecimal fixedAmount,
        @PositiveOrZero BigDecimal minOrderAmount,
        @PositiveOrZero BigDecimal maxDiscountAmount,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Integer priority
) {
}
