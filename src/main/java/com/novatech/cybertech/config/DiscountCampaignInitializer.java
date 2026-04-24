package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.repositories.DiscountCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscountCampaignInitializer implements ApplicationRunner {

    private final DiscountCampaignRepository discountCampaignRepository;

    @Override
    public void run(final ApplicationArguments args) {
        for (final DiscountType type : DiscountType.values()) {
            discountCampaignRepository.findByDiscountType(type)
                    .orElseGet(() -> {
                        final DiscountCampaignEntity saved = discountCampaignRepository.save(defaultCampaign(type));
                        log.info("Seeded missing discount campaign for {}", type);
                        return saved;
                    });
        }
    }

    private DiscountCampaignEntity defaultCampaign(final DiscountType type) {
        return DiscountCampaignEntity.builder()
                .discountType(type)
                .calculationType(resolveDefaultCalculationType(type))
                .enabled(type == DiscountType.NO_DISCOUNT)
                .percentage(defaultPercentage(type))
                .priority(defaultPriority(type))
                .build();
    }

    private DiscountCalculationType resolveDefaultCalculationType(final DiscountType type) {
        return switch (type) {
            case NO_DISCOUNT -> DiscountCalculationType.NONE;
            case BLACK_FRIDAY, WINTER_SALES, SPRING_SALES -> DiscountCalculationType.PERCENTAGE;
            case BUY_ONE_GET_ONE_FREE -> DiscountCalculationType.BUY_ONE_GET_ONE_FREE;
        };
    }

    private BigDecimal defaultPercentage(final DiscountType type) {
        return switch (type) {
            case BLACK_FRIDAY -> BigDecimal.valueOf(20);
            case WINTER_SALES -> BigDecimal.valueOf(15);
            case SPRING_SALES -> BigDecimal.valueOf(10);
            default -> null;
        };
    }

    private int defaultPriority(final DiscountType type) {
        return switch (type) {
            case BLACK_FRIDAY -> 100;
            case WINTER_SALES -> 80;
            case SPRING_SALES -> 60;
            case BUY_ONE_GET_ONE_FREE -> 70;
            case NO_DISCOUNT -> 0;
        };
    }
}
