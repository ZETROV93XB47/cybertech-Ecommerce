package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.repositories.DiscountCampaignRepository;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountCampaignServiceImp implements DiscountCampaignService {

    static final String DISCOUNT_CAMPAIGNS_CACHE = "discountCampaigns";

    private final DiscountCampaignRepository discountCampaignRepository;

    @Override
    @Cacheable(value = DISCOUNT_CAMPAIGNS_CACHE, key = "#discountKey")
    public DiscountContext getActiveDiscountContext(final String discountKey) {
        final DiscountCampaignEntity campaign = discountCampaignRepository
                .findByDiscountKey(discountKey)
                .orElseThrow(() -> new DiscountTypeNotActiveException(
                        "Discount " + discountKey + " does not exist"));

        if (!campaign.isEnabled()) {
            throw new DiscountTypeNotActiveException("Discount " + discountKey + " is disabled");
        }

        final LocalDateTime now = LocalDateTime.now();

        if (campaign.getStartsAt() != null && now.isBefore(campaign.getStartsAt())) {
            throw new DiscountTypeNotActiveException("Discount " + discountKey + " has not started yet");
        }

        if (campaign.getEndsAt() != null && now.isAfter(campaign.getEndsAt())) {
            throw new DiscountTypeNotActiveException("Discount " + discountKey + " has expired");
        }

        log.debug("Resolved active discount context for {}", discountKey);

        return DiscountContext.builder()
                .discountKey(campaign.getDiscountKey())
                .calculationType(campaign.getCalculationType())
                .percentage(campaign.getPercentage())
                .fixedAmount(campaign.getFixedAmount())
                .minOrderAmount(campaign.getMinOrderAmount())
                .maxDiscountAmount(campaign.getMaxDiscountAmount())
                .startsAt(campaign.getStartsAt())
                .endsAt(campaign.getEndsAt())
                .build();
    }

    @Override
    public List<DiscountContext> getAllActiveCampaigns() {
        final LocalDateTime now = LocalDateTime.now();
        return discountCampaignRepository.findByEnabledTrue().stream()
                .filter(c -> c.getStartsAt() == null || !now.isBefore(c.getStartsAt()))
                .filter(c -> c.getEndsAt() == null || !now.isAfter(c.getEndsAt()))
                .map(this::toContext)
                .toList();
    }

    private DiscountContext toContext(final DiscountCampaignEntity campaign) {
        return DiscountContext.builder()
                .discountKey(campaign.getDiscountKey())
                .calculationType(campaign.getCalculationType())
                .percentage(campaign.getPercentage())
                .fixedAmount(campaign.getFixedAmount())
                .minOrderAmount(campaign.getMinOrderAmount())
                .maxDiscountAmount(campaign.getMaxDiscountAmount())
                .startsAt(campaign.getStartsAt())
                .endsAt(campaign.getEndsAt())
                .build();
    }

    @CacheEvict(value = DISCOUNT_CAMPAIGNS_CACHE, key = "#discountKey")
    public void evictCache(final String discountKey) {
        log.debug("Evicted discount cache for {}", discountKey);
    }

    @CacheEvict(value = DISCOUNT_CAMPAIGNS_CACHE, allEntries = true)
    public void evictAllCache() {
        log.debug("Evicted all discount campaign cache entries");
    }
}
