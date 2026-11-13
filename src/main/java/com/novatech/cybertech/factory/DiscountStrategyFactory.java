package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.strategy.discount.DiscountStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves a {@link DiscountStrategy} from a {@link DiscountCalculationType}.
 *
 * <p>The map is keyed on the calculation algorithm, not the commercial discount type,
 * so multiple campaigns (BLACK_FRIDAY, WINTER_SALES, …) sharing the same algorithm
 * (PERCENTAGE) reuse a single strategy bean.
 */
@Component
@RequiredArgsConstructor
public class DiscountStrategyFactory {
    private final Map<DiscountCalculationType, DiscountStrategy> strategyMap;

    public DiscountStrategy getStrategy(final DiscountCalculationType type) {
        return strategyMap.get(type);
    }
}
