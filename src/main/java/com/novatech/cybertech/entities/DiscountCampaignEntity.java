package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
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
public class DiscountCampaignEntity extends BaseEntity<Long> {

    @Enumerated(EnumType.STRING)
    @Column(name = "discountType", nullable = false, unique = true, length = 50)
    private DiscountType discountType;

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
