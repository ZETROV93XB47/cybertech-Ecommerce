package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.strategy.discount.DiscountStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DiscountStrategyFactory {
    private final Map<DiscountType, DiscountStrategy> strategyMap;

    public DiscountStrategy getStrategy(DiscountType type) {
        return strategyMap.get(type);
    }
}
