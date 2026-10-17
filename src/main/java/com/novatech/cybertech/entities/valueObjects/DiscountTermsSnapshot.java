package com.novatech.cybertech.entities.valueObjects;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Frozen copy of the discount campaign terms an order was placed under.
 *
 * <p>An order keeps these terms for its whole life: a later modification re-prices the new lines
 * with them, NOT with the campaign's current configuration — the campaign may have been disabled,
 * edited or may have expired in the meantime, which must neither break the modification nor
 * change what the customer was granted. The campaign key itself stays on
 * {@code OrderEntity#discountKey}.
 *
 * <p>Every column is nullable: orders placed before this snapshot existed load with all fields
 * {@code null} (Hibernate then leaves the embedded attribute {@code null}).
 */
@Getter
@Builder
@ToString
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class DiscountTermsSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "discountCalculationType")
    private DiscountCalculationType calculationType;

    @Column(name = "discountPercentage", precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "discountFixedAmount", precision = 19, scale = 2)
    private BigDecimal fixedAmount;

    @Column(name = "discountMinOrderAmount", precision = 19, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "discountMaxAmount", precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    /**
     * Snapshot the pricing terms of a resolved campaign (validity window left out: the order
     * keeps the terms regardless of the campaign's dates).
     *
     * @param context the campaign context the order was priced with; may be {@code null}.
     * @return the snapshot, or {@code null} when {@code context} is {@code null}.
     */
    public static DiscountTermsSnapshot from(final DiscountContext context) {
        if (context == null) {
            return null;
        }
        return DiscountTermsSnapshot.builder()
                .calculationType(context.calculationType())
                .percentage(context.percentage())
                .fixedAmount(context.fixedAmount())
                .minOrderAmount(context.minOrderAmount())
                .maxDiscountAmount(context.maxDiscountAmount())
                .build();
    }

    /**
     * Rebuild a {@link DiscountContext} from these frozen terms, for re-pricing an order.
     *
     * @param discountKey the order's campaign key, carried for logging / error messages.
     * @return a context without validity window (the terms apply unconditionally).
     */
    public DiscountContext toDiscountContext(final String discountKey) {
        return DiscountContext.builder()
                .discountKey(discountKey)
                .calculationType(calculationType)
                .percentage(percentage)
                .fixedAmount(fixedAmount)
                .minOrderAmount(minOrderAmount)
                .maxDiscountAmount(maxDiscountAmount)
                .build();
    }
}
