package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.repositories.DiscountCampaignRepository;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountCampaignServiceImp implements DiscountCampaignService {

    static final String DISCOUNT_CAMPAIGNS_CACHE = "discountCampaigns";

    private final DiscountCampaignRepository discountCampaignRepository;

    @Override
    @Cacheable(value = DISCOUNT_CAMPAIGNS_CACHE, key = "#discountType.name()")
    public DiscountContext getActiveDiscountContext(final DiscountType discountType) {
        final DiscountCampaignEntity campaign = discountCampaignRepository
                .findByDiscountType(discountType)
                .orElseThrow(() -> new DiscountTypeNotActiveException(
                        "Discount " + discountType + " does not exist"));

        if (!campaign.isEnabled()) {
            throw new DiscountTypeNotActiveException("Discount " + discountType + " is disabled");
        }

        final LocalDateTime now = LocalDateTime.now();

        if (campaign.getStartsAt() != null && now.isBefore(campaign.getStartsAt())) {
            throw new DiscountTypeNotActiveException("Discount " + discountType + " has not started yet");
        }

        if (campaign.getEndsAt() != null && now.isAfter(campaign.getEndsAt())) {
            throw new DiscountTypeNotActiveException("Discount " + discountType + " has expired");
        }

        log.debug("Resolved active discount context for {}", discountType);

        return DiscountContext.builder()
                .discountType(campaign.getDiscountType())
                .calculationType(campaign.getCalculationType())
                .percentage(campaign.getPercentage())
                .fixedAmount(campaign.getFixedAmount())
                .minOrderAmount(campaign.getMinOrderAmount())
                .maxDiscountAmount(campaign.getMaxDiscountAmount())
                .startsAt(campaign.getStartsAt())
                .endsAt(campaign.getEndsAt())
                .build();
    }

    @CacheEvict(value = DISCOUNT_CAMPAIGNS_CACHE, key = "#discountType.name()")
    public void evictCache(final DiscountType discountType) {
        log.debug("Evicted discount cache for {}", discountType);
    }

    @CacheEvict(value = DISCOUNT_CAMPAIGNS_CACHE, allEntries = true)
    public void evictAllCache() {
        log.debug("Evicted all discount campaign cache entries");
    }
}
