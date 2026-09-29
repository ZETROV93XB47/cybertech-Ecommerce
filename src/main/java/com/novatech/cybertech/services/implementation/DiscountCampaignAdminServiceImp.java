package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.admin.DiscountCampaignCreateRequestDto;
import com.novatech.cybertech.dto.request.admin.DiscountCampaignUpdateRequestDto;
import com.novatech.cybertech.dto.response.admin.DiscountCampaignResponseDto;
import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
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
    private final DiscountStrategyFactory discountStrategyFactory;

    @Override
    @Transactional(readOnly = true)
    public List<DiscountCampaignResponseDto> getAll() {
        return discountCampaignRepository.findAll().stream()
                .sorted(Comparator.comparing(DiscountCampaignEntity::getDiscountKey))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DiscountCampaignResponseDto getByDiscountKey(final String discountKey) {
        return toResponse(loadOrThrow(discountKey));
    }

    @Override
    @Transactional
    public DiscountCampaignResponseDto create(final DiscountCampaignCreateRequestDto request) {
        if (discountCampaignRepository.existsByDiscountKey(request.discountKey())) {
            throw new IllegalArgumentException("A discount campaign '" + request.discountKey() + "' already exists");
        }

        // Fail fast on an algorithm nothing implements — never persist a campaign price
        // calculation can never resolve a strategy for. Mirrors ProductCategorySchemaServiceImp's
        // compile-before-persist guard on jsonSchema.
        if (discountStrategyFactory.getStrategy(request.calculationType()) == null) {
            throw new NoStrategyFoundForProcessingTheRequest(
                    "No DiscountStrategy wired for calculation type " + request.calculationType());
        }

        final DiscountCampaignEntity saved = discountCampaignRepository.save(DiscountCampaignEntity.builder()
                .discountKey(request.discountKey())
                .calculationType(request.calculationType())
                .enabled(request.enabled() != null && request.enabled())
                .percentage(request.percentage())
                .fixedAmount(request.fixedAmount())
                .minOrderAmount(request.minOrderAmount())
                .maxDiscountAmount(request.maxDiscountAmount())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .priority(request.priority())
                .build());

        log.info("Created discount campaign '{}' (calc={}, enabled={})",
                saved.getDiscountKey(), saved.getCalculationType(), saved.isEnabled());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DiscountCampaignResponseDto update(final String discountKey,
                                              final DiscountCampaignUpdateRequestDto request) {
        final DiscountCampaignEntity campaign = loadOrThrow(discountKey);

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

        // Cache key is the discountKey — evict so the next price calc reads the fresh row.
        discountCampaignService.evictCache(discountKey);
        log.info("Updated discount campaign {} (enabled={}, calc={}, pct={}, fixed={})",
                discountKey, saved.isEnabled(), saved.getCalculationType(),
                saved.getPercentage(), saved.getFixedAmount());

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteByDiscountKey(final String discountKey) {
        final DiscountCampaignEntity campaign = loadOrThrow(discountKey);
        discountCampaignRepository.delete(campaign);
        discountCampaignService.evictCache(discountKey);
        log.info("Deleted discount campaign '{}'", discountKey);
    }

    private DiscountCampaignEntity loadOrThrow(final String discountKey) {
        return discountCampaignRepository.findByDiscountKey(discountKey)
                .orElseThrow(() -> new DiscountTypeNotActiveException(
                        "Discount campaign for " + discountKey + " does not exist"));
    }

    private DiscountCampaignResponseDto toResponse(final DiscountCampaignEntity entity) {
        return DiscountCampaignResponseDto.builder()
                .uuid(entity.getUuid())
                .discountKey(entity.getDiscountKey())
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
