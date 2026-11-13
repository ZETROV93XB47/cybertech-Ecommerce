package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.repositories.DiscountCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_DISCOUNT_KEY;

/**
 * Seeds a starter set of discount campaigns on a fresh {@code discount_campaign} table —
 * convenience defaults, not a closed list. {@code NO_DISCOUNT_KEY} is the only one that MUST
 * exist ({@code OrderPriceCalculationServiceImp} short-circuits on it); the rest are demo
 * campaigns a fresh deploy starts with. Any further campaign is created at runtime via
 * {@code DiscountCampaignAdminService#create} — no code change / redeploy needed, which is the
 * whole point of {@code discountKey} being a free-form String instead of an enum.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscountCampaignInitializer implements ApplicationRunner {

    private static final List<String> STARTER_CAMPAIGN_KEYS =
            List.of(NO_DISCOUNT_KEY, "BLACK_FRIDAY", "WINTER_SALES", "SPRING_SALES", "BUY_ONE_GET_ONE_FREE");

    private final DiscountCampaignRepository discountCampaignRepository;

    @Override
    public void run(final ApplicationArguments args) {
        for (final String key : STARTER_CAMPAIGN_KEYS) {
            discountCampaignRepository.findByDiscountKey(key)
                    .orElseGet(() -> {
                        final DiscountCampaignEntity saved = discountCampaignRepository.save(defaultCampaign(key));
                        log.info("Seeded missing discount campaign for {}", key);
                        return saved;
                    });
        }
    }

    private DiscountCampaignEntity defaultCampaign(final String key) {
        return DiscountCampaignEntity.builder()
                .discountKey(key)
                .calculationType(resolveDefaultCalculationType(key))
                .enabled(key.equals(NO_DISCOUNT_KEY))
                .percentage(defaultPercentage(key))
                .priority(defaultPriority(key))
                .build();
    }

    private DiscountCalculationType resolveDefaultCalculationType(final String key) {
        return switch (key) {
            case NO_DISCOUNT_KEY -> DiscountCalculationType.NONE;
            case "BLACK_FRIDAY", "WINTER_SALES", "SPRING_SALES" -> DiscountCalculationType.PERCENTAGE;
            case "BUY_ONE_GET_ONE_FREE" -> DiscountCalculationType.BUY_ONE_GET_ONE_FREE;
            default -> DiscountCalculationType.NONE;
        };
    }

    private BigDecimal defaultPercentage(final String key) {
        return switch (key) {
            case "BLACK_FRIDAY" -> BigDecimal.valueOf(20);
            case "WINTER_SALES" -> BigDecimal.valueOf(15);
            case "SPRING_SALES" -> BigDecimal.valueOf(10);
            default -> null;
        };
    }

    private int defaultPriority(final String key) {
        return switch (key) {
            case "BLACK_FRIDAY" -> 100;
            case "WINTER_SALES" -> 80;
            case "SPRING_SALES" -> 60;
            case "BUY_ONE_GET_ONE_FREE" -> 70;
            default -> 0;
        };
    }
}
