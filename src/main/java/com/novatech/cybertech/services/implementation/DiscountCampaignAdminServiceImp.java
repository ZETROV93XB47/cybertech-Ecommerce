package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.admin.DiscountCampaignCreateRequestDto;
import com.novatech.cybertech.dto.request.admin.DiscountCampaignUpdateRequestDto;
import com.novatech.cybertech.dto.response.admin.DiscountCampaignResponseDto;
import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.exceptions.DiscountCampaignAlreadyExistsException;
import com.novatech.cybertech.exceptions.DiscountCampaignMissingRequiredFieldException;
import com.novatech.cybertech.exceptions.DiscountCampaignNotFoundException;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.factory.DiscountStrategyFactory;
import com.novatech.cybertech.repositories.DiscountCampaignRepository;
import com.novatech.cybertech.services.core.DiscountCampaignAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
            throw new DiscountCampaignAlreadyExistsException(alreadyExistsMessage(request.discountKey()));
        }

        // Fail fast on an algorithm nothing implements — never persist a campaign price
        // calculation can never resolve a strategy for. Mirrors ProductCategorySchemaServiceImp's
        // compile-before-persist guard on jsonSchema.
        if (discountStrategyFactory.getStrategy(request.calculationType()) == null) {
            throw new NoStrategyFoundForProcessingTheRequest(
                    "No DiscountStrategy wired for calculation type " + request.calculationType());
        }

        // Fail fast on a strategy that IS wired but would have nothing to compute against —
        // without this, an admin could persist e.g. calculationType=PERCENTAGE with no percentage,
        // and PercentageDiscountStrategy would only discover the gap (via its own defensive,
        // unmapped IllegalStateException) when a real customer's order priced against this campaign.
        requireFieldForCalculationType(request.calculationType(), request.percentage(), request.fixedAmount());

        final DiscountCampaignEntity saved = saveNewCampaign(DiscountCampaignEntity.builder()
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

        // Validate the MERGED state (not just the patch): a PATCH that only touches minOrderAmount
        // must not silently let a stale percentage=null survive if calculationType is PERCENTAGE —
        // and conversely, switching calculationType to PERCENTAGE without supplying percentage in
        // the same call must be caught here too.
        requireFieldForCalculationType(campaign.getCalculationType(), campaign.getPercentage(), campaign.getFixedAmount());

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

    /**
     * Mirrors the field each {@code DiscountStrategy} implementation actually dereferences —
     * {@link com.novatech.cybertech.strategy.discount.PercentageDiscountStrategy} requires
     * {@code percentage}, {@link com.novatech.cybertech.strategy.discount.FixedAmountDiscountStrategy}
     * requires {@code fixedAmount}. {@code NONE} and {@code BUY_ONE_GET_ONE_FREE} need neither.
     */
    private void requireFieldForCalculationType(final DiscountCalculationType calculationType,
                                                 final BigDecimal percentage,
                                                 final BigDecimal fixedAmount) {
        if (calculationType == DiscountCalculationType.PERCENTAGE && percentage == null) {
            throw new DiscountCampaignMissingRequiredFieldException(
                    "calculationType PERCENTAGE requires a non-null percentage");
        }
        if (calculationType == DiscountCalculationType.FIXED_AMOUNT && fixedAmount == null) {
            throw new DiscountCampaignMissingRequiredFieldException(
                    "calculationType FIXED_AMOUNT requires a non-null fixedAmount");
        }
    }

    private DiscountCampaignEntity loadOrThrow(final String discountKey) {
        return discountCampaignRepository.findByDiscountKey(discountKey)
                .orElseThrow(() -> new DiscountCampaignNotFoundException(
                        "Discount campaign for " + discountKey + " does not exist"));
    }

    /**
     * The {@code existsByDiscountKey} pre-check is a plain SELECT: two concurrent creates of the same
     * key can both pass it. The unique constraint on the key column is what actually rejects the
     * second insert — surfaced as the same 409 instead of a raw 500 (same pattern as
     * {@code WishlistServiceImp#addProductToMyWishlist}). IDENTITY ids make {@code save} insert
     * immediately, so the violation is raised here rather than at commit.
     */
    private DiscountCampaignEntity saveNewCampaign(final DiscountCampaignEntity campaign) {
        try {
            return discountCampaignRepository.save(campaign);
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent create of discount campaign '{}' — caught by the unique constraint", campaign.getDiscountKey());
            throw new DiscountCampaignAlreadyExistsException(alreadyExistsMessage(campaign.getDiscountKey()), e);
        }
    }

    private static String alreadyExistsMessage(final String discountKey) {
        return "A discount campaign '" + discountKey + "' already exists";
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
