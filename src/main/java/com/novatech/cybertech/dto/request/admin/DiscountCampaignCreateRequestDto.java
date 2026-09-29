package com.novatech.cybertech.dto.request.admin;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registers a new discount campaign. {@code calculationType} must have a
 * {@code DiscountStrategy} bean wired for it (checked before persisting — see
 * {@code DiscountCampaignAdminServiceImp}) — the set of ALGORITHMS is still fixed in code
 * (PERCENTAGE / FIXED_AMOUNT / BUY_ONE_GET_ONE_FREE / NONE), but {@code discountKey} — the
 * campaign's own identity — is free-form: creating a new campaign that reuses an existing
 * algorithm needs no code change or redeploy.
 */
public record DiscountCampaignCreateRequestDto(
        @NotBlank(message = "discountKey cannot be blank") String discountKey,
        @NotNull(message = "calculationType cannot be null") DiscountCalculationType calculationType,
        Boolean enabled,
        @DecimalMin(value = "0.00") @DecimalMax(value = "100.00") BigDecimal percentage,
        @PositiveOrZero BigDecimal fixedAmount,
        @PositiveOrZero BigDecimal minOrderAmount,
        @PositiveOrZero BigDecimal maxDiscountAmount,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Integer priority
) {
}
