package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.enums.DiscountType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
@ConfigurationProperties(prefix = "active-discounts")
public class ActiveDiscountsProperties {

    private boolean noDiscount = true;
    private boolean blackFriday = false;
    private boolean winterSales = false;
    private boolean springSales = false;
    private boolean buyOneGetOneFree = false;

    public boolean isActive(final DiscountType type) {
        if (type == null) return false;
        return switch (type) {
            case NO_DISCOUNT -> noDiscount;
            case BLACK_FRIDAY -> blackFriday;
            case WINTER_SALES -> winterSales;
            case SPRING_SALES -> springSales;
            case BUY_ONE_GET_ONE_FREE -> buyOneGetOneFree;
        };
    }

    public Set<DiscountType> getEnabled() {
        return Stream.of(DiscountType.values())
                .filter(this::isActive)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DiscountType.class)));
    }
}
