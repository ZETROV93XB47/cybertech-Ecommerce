package com.novatech.cybertech.dto.data;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import lombok.Builder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record DiscountContext(
        DiscountType discountType,
        DiscountCalculationType calculationType,
        BigDecimal percentage,
        BigDecimal fixedAmount,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
