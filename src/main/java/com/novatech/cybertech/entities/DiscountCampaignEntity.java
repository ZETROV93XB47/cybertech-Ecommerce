package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "discount_campaign",
        indexes = {
                @Index(name = "idx_discount_campaign_type", columnList = "discountType"),
                @Index(name = "idx_discount_campaign_enabled", columnList = "enabled")
        }
)
@ToString(callSuper = true)
public class DiscountCampaignEntity extends BaseEntity<Long> {

    /**
     * Free-form campaign identifier chosen by the admin at creation time (e.g. {@code
     * "BLACK_FRIDAY"}, {@code "SUMMER_FLASH_SALE_2027"}) — replaces the previous hardcoded {@code
     * DiscountType} enum, so a new campaign is an admin API call, not a redeploy. Mirrors {@link
     * ProductCategorySchemaEntity#getCategoryKey()}.
     *
     * <p>Column name kept as {@code discountType} (not renamed to {@code discountKey}) so {@code
     * ddl-auto=update} does not orphan the existing column / existing seeded rows — this project
     * has no migration tool (see {@code progress.md}).
     */
    @Column(name = "discountType", nullable = false, unique = true, length = 50)
    private String discountKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculationType", nullable = false, length = 50)
    private DiscountCalculationType calculationType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "fixedAmount", precision = 10, scale = 2)
    private BigDecimal fixedAmount;

    @Column(name = "minOrderAmount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "maxDiscountAmount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "startsAt")
    private LocalDateTime startsAt;

    @Column(name = "endsAt")
    private LocalDateTime endsAt;

    @Column(name = "priority")
    private Integer priority;
}
