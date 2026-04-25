package com.novatech.cybertech.dto.response.admin;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-side projection of a {@code DiscountCampaignEntity} for the admin endpoint.
 */
@Builder
public record DiscountCampaignResponseDto(
        UUID uuid,
        DiscountType discountType,
        DiscountCalculationType calculationType,
        boolean enabled,
        BigDecimal percentage,
        BigDecimal fixedAmount,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Integer priority
) {
}
