package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.clients.GorseClient;
import com.novatech.cybertech.dto.response.gorse.GorseScoredItemDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.mappers.entity.ProductMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.services.core.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads Gorse's precomputed recommendations with a 3-tier cascade — personalized
 * ({@code GET /api/recommend/{userId}}) → catalog-wide latest ({@code GET /api/latest}) →
 * best-sellers ({@link ProductRepository#findBestSellers}, 100% local, no Gorse dependency) —
 * so a Gorse outage degrades the home page instead of breaking it. Each tier is isolated by its
 * own try/catch, same philosophy as {@code GorseSyncTasklet}'s per-phase isolation.
 *
 * <p>Cache-aside on Redis ({@code recommendations::&lt;userId&gt;}, TTL {@code
 * cybertech.recommendation.cache.ttl-seconds}) using the same {@code RedisTemplate<String,
 * Object>} bean {@code CartCacheHelperImp} uses — cost scales with active users, not total
 * population, which is why this stays a live proxy rather than a batch job that materializes
 * recommendations for every user up front.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImp implements RecommendationService {

    private static final String RECOMMENDATION_CACHE_KEY_PREFIX = "recommendations::";

    private final GorseClient gorseClient;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${cybertech.recommendation.cache.ttl-seconds:900}")
    private int cacheTtlSeconds;

    @Override
    public List<ProductResponseDto> getRecommendedProducts(final String userId, final int n) {
        final String cacheKey = cacheKey(userId);
        final List<ProductResponseDto> cached = getCached(cacheKey);
        if (cached != null) {
            return cached;
        }

        final List<ProductResponseDto> recommendations = fromGorseRecommend(userId, n)
                .or(() -> fromGorseLatest(n))
                .orElseGet(() -> fromBestSellers(n));

        redisTemplate.opsForValue().set(cacheKey, recommendations, Duration.ofSeconds(cacheTtlSeconds));
        return recommendations;
    }

    private Optional<List<ProductResponseDto>> fromGorseRecommend(final String userId, final int n) {
        log.info("Calling Gorse recommend for user {} (n={})", userId, n);
        try {
            final List<String> itemIds = gorseClient.getRecommendations(userId, n);
            return itemIds.isEmpty() ? Optional.empty() : Optional.of(resolveProducts(itemIds));
        } catch (Exception e) {
            log.warn("Gorse recommend call failed for user {} — falling back to latest items", userId, e);
            return Optional.empty();
        }
    }

    private Optional<List<ProductResponseDto>> fromGorseLatest(final int n) {
        log.info("Calling Gorse latest-items (n={})", n);
        try {
            final List<String> itemIds = gorseClient.getLatestItems(n).stream()
                    .map(GorseScoredItemDto::getId)
                    .toList();
            return itemIds.isEmpty() ? Optional.empty() : Optional.of(resolveProducts(itemIds));
        } catch (Exception e) {
            log.warn("Gorse latest-items call failed — falling back to best sellers", e);
            return Optional.empty();
        }
    }

    private List<ProductResponseDto> fromBestSellers(final int n) {
        log.info("Falling back to best sellers (n={})", n);
        return productRepository.findBestSellers(PageRequest.of(0, n)).stream()
                .map(productMapper::mapFromEntityToResponseDto)
                .toList();
    }

    /**
     * Resolves item ids to {@link ProductResponseDto}, preserving Gorse's ranking order — a SQL
     * {@code IN} doesn't guarantee row order, so results are re-ordered via a lookup map. An id
     * that no longer parses as a UUID or no longer matches a product (deleted since it was last
     * synced to Gorse) is silently skipped rather than failing the whole tier.
     */
    private List<ProductResponseDto> resolveProducts(final List<String> itemIdsInOrder) {
        final List<UUID> uuids = itemIdsInOrder.stream()
                .map(this::parseUuid)
                .filter(Objects::nonNull)
                .toList();
        final Map<UUID, ProductEntity> byUuid = productRepository.findAllByUuidIn(uuids).stream()
                .collect(Collectors.toMap(ProductEntity::getUuid, Function.identity()));
        return uuids.stream()
                .map(byUuid::get)
                .filter(Objects::nonNull)
                .map(productMapper::mapFromEntityToResponseDto)
                .toList();
    }

    private UUID parseUuid(final String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Gorse returned a non-UUID item id {} — skipping", raw);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<ProductResponseDto> getCached(final String cacheKey) {
        return (List<ProductResponseDto>) redisTemplate.opsForValue().get(cacheKey);
    }

    private static String cacheKey(final String userId) {
        return RECOMMENDATION_CACHE_KEY_PREFIX + userId;
    }
}
