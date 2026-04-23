package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.enums.DiscountType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "active-discounts")
public class ActiveDiscountsProperties {

    private Set<DiscountType> enabled = EnumSet.of(DiscountType.NO_DISCOUNT);

    public boolean isActive(final DiscountType type) {
        return type != null && enabled.contains(type);
    }
}
