package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.admin.DiscountCampaignUpdateRequestDto;
import com.novatech.cybertech.dto.response.admin.DiscountCampaignResponseDto;
import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.repositories.DiscountCampaignRepository;
import com.novatech.cybertech.services.core.DiscountCampaignAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Admin-facing CRUD on the {@code discount_campaign} table.
 *
 * <p>The runtime read-side cache lives on {@link DiscountCampaignServiceImp}; this admin
 * service evicts the cache after any mutation so {@code OrderPriceCalculationService}
 * picks up the new config on the next call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountCampaignAdminServiceImp implements DiscountCampaignAdminService {

    private final DiscountCampaignRepository discountCampaignRepository;
    private final DiscountCampaignServiceImp discountCampaignService;

    @Override
    @Transactional(readOnly = true)
    public List<DiscountCampaignResponseDto> getAll() {
        return discountCampaignRepository.findAll().stream()
                .sorted(Comparator.comparing(DiscountCampaignEntity::getDiscountType))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DiscountCampaignResponseDto getByDiscountType(final DiscountType discountType) {
        return toResponse(loadOrThrow(discountType));
    }

    @Override
    @Transactional
    public DiscountCampaignResponseDto update(final DiscountType discountType,
                                              final DiscountCampaignUpdateRequestDto request) {
        final DiscountCampaignEntity campaign = loadOrThrow(discountType);

        if (request.enabled() != null) {
            campaign.setEnabled(request.enabled());
        }
        if (request.calculationType() != null) {
            campaign.setCalculationType(request.calculationType());
        }
        if (request.percentage() != null) {
            campaign.setPercentage(request.percentage());
        }
        if (request.fixedAmount() != null) {
            campaign.setFixedAmount(request.fixedAmount());
        }
        if (request.minOrderAmount() != null) {
            campaign.setMinOrderAmount(request.minOrderAmount());
        }
        if (request.maxDiscountAmount() != null) {
            campaign.setMaxDiscountAmount(request.maxDiscountAmount());
        }
        if (request.startsAt() != null) {
            campaign.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            campaign.setEndsAt(request.endsAt());
        }
        if (request.priority() != null) {
            campaign.setPriority(request.priority());
        }

        final DiscountCampaignEntity saved = discountCampaignRepository.save(campaign);

        // Cache key is by DiscountType — evict so the next price calc reads the fresh row.
        discountCampaignService.evictCache(discountType);
        log.info("Updated discount campaign {} (enabled={}, calc={}, pct={}, fixed={})",
                discountType, saved.isEnabled(), saved.getCalculationType(),
                saved.getPercentage(), saved.getFixedAmount());

        return toResponse(saved);
    }

    private DiscountCampaignEntity loadOrThrow(final DiscountType discountType) {
        return discountCampaignRepository.findByDiscountType(discountType)
                .orElseThrow(() -> new DiscountTypeNotActiveException(
                        "Discount campaign for " + discountType + " does not exist"));
    }

    private DiscountCampaignResponseDto toResponse(final DiscountCampaignEntity entity) {
        return DiscountCampaignResponseDto.builder()
                .uuid(entity.getUuid())
                .discountType(entity.getDiscountType())
                .calculationType(entity.getCalculationType())
                .enabled(entity.isEnabled())
                .percentage(entity.getPercentage())
                .fixedAmount(entity.getFixedAmount())
                .minOrderAmount(entity.getMinOrderAmount())
                .maxDiscountAmount(entity.getMaxDiscountAmount())
                .startsAt(entity.getStartsAt())
                .endsAt(entity.getEndsAt())
                .priority(entity.getPriority())
                .build();
    }
}
