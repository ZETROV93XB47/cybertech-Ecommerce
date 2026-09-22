package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.clients.GorseClient;
import com.novatech.cybertech.dto.response.gorse.GorseScoredItemDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.mappers.entity.ProductMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecommendationServiceImp} — cache-aside behaviour and the
 * recommend → latest → best-sellers cascade, including per-tier isolation and Gorse ranking-order
 * preservation.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceImpTest {

    private static final String USER_ID = "keycloak-user";
    private static final int N = 2;
    private static final int TTL_SECONDS = 900;

    @Mock
    private GorseClient gorseClient;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RecommendationServiceImp service;

    @BeforeEach
    void setUp() {
        service = new RecommendationServiceImp(gorseClient, productRepository, productMapper, redisTemplate);
        ReflectionTestUtils.setField(service, "cacheTtlSeconds", TTL_SECONDS);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private ProductEntity productWithUuid(final UUID uuid) {
        return ProductEntityBuilder.aValidProductBuilder().uuid(uuid).build();
    }

    private ProductResponseDto responseDtoFor(final ProductEntity product) {
        return ProductResponseDto.builder().uuid(product.getUuid().toString()).build();
    }

    @Nested
    @DisplayName("Cache")
    class Cache {

        @Test
        @DisplayName("cache hit short-circuits — GorseClient is never called")
        void cacheHit_skipsGorse() {
            final List<ProductResponseDto> cached = List.of(ProductResponseDto.builder().uuid("cached").build());
            when(valueOperations.get("recommendations::" + USER_ID)).thenReturn(cached);

            final List<ProductResponseDto> result = service.getRecommendedProducts(USER_ID, N);

            assertThat(result).isEqualTo(cached);
            verify(gorseClient, never()).getRecommendations(any(), anyInt());
        }

        @Test
        @DisplayName("cache miss writes the resolved result back with the configured TTL")
        void cacheMiss_writesResultWithTtl() {
            when(valueOperations.get(any())).thenReturn(null);
            when(gorseClient.getRecommendations(eq(USER_ID), eq(N))).thenReturn(List.of());
            when(gorseClient.getLatestItems(N)).thenReturn(List.of());
            when(productRepository.findBestSellers(any(Pageable.class))).thenReturn(Page.empty());

            service.getRecommendedProducts(USER_ID, N);

            final ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
            verify(valueOperations).set(eq("recommendations::" + USER_ID), valueCaptor.capture(), eq(Duration.ofSeconds(TTL_SECONDS)));
            assertThat(valueCaptor.getValue()).isEqualTo(List.of());
        }
    }

    @Nested
    @DisplayName("Cascade")
    class Cascade {

        @Test
        @DisplayName("Gorse recommend succeeds → used directly, latest/best-sellers never called")
        void gorseRecommend_succeeds_usedDirectly() {
            final UUID uuid1 = UUID.randomUUID();
            final UUID uuid2 = UUID.randomUUID();
            final ProductEntity p1 = productWithUuid(uuid1);
            final ProductEntity p2 = productWithUuid(uuid2);
            when(valueOperations.get(any())).thenReturn(null);
            when(gorseClient.getRecommendations(USER_ID, N)).thenReturn(List.of(uuid1.toString(), uuid2.toString()));
            when(productRepository.findAllByUuidIn(List.of(uuid1, uuid2))).thenReturn(List.of(p1, p2));
            when(productMapper.mapFromEntityToResponseDto(p1)).thenReturn(responseDtoFor(p1));
            when(productMapper.mapFromEntityToResponseDto(p2)).thenReturn(responseDtoFor(p2));

            final List<ProductResponseDto> result = service.getRecommendedProducts(USER_ID, N);

            assertThat(result).extracting(ProductResponseDto::getUuid)
                    .containsExactly(uuid1.toString(), uuid2.toString());
            verify(gorseClient, never()).getLatestItems(anyInt());
            verify(productRepository, never()).findBestSellers(any(Pageable.class));
        }

        @Test
        @DisplayName("Gorse recommend returns empty (cold start) → falls back to latest")
        void gorseRecommend_empty_fallsBackToLatest() {
            final UUID uuid = UUID.randomUUID();
            final ProductEntity product = productWithUuid(uuid);
            when(valueOperations.get(any())).thenReturn(null);
            when(gorseClient.getRecommendations(USER_ID, N)).thenReturn(List.of());
            when(gorseClient.getLatestItems(N)).thenReturn(List.of(new GorseScoredItemDto(uuid.toString(), 1.0)));
            when(productRepository.findAllByUuidIn(List.of(uuid))).thenReturn(List.of(product));
            when(productMapper.mapFromEntityToResponseDto(product)).thenReturn(responseDtoFor(product));

            final List<ProductResponseDto> result = service.getRecommendedProducts(USER_ID, N);

            assertThat(result).extracting(ProductResponseDto::getUuid).containsExactly(uuid.toString());
            verify(productRepository, never()).findBestSellers(any(Pageable.class));
        }

        @Test
        @DisplayName("Gorse recommend throws → falls back to latest, exception is not propagated")
        void gorseRecommend_throws_fallsBackToLatest() {
            final UUID uuid = UUID.randomUUID();
            final ProductEntity product = productWithUuid(uuid);
            when(valueOperations.get(any())).thenReturn(null);
            when(gorseClient.getRecommendations(USER_ID, N)).thenThrow(new RuntimeException("Gorse down"));
            when(gorseClient.getLatestItems(N)).thenReturn(List.of(new GorseScoredItemDto(uuid.toString(), 1.0)));
            when(productRepository.findAllByUuidIn(List.of(uuid))).thenReturn(List.of(product));
            when(productMapper.mapFromEntityToResponseDto(product)).thenReturn(responseDtoFor(product));

            final List<ProductResponseDto> result = service.getRecommendedProducts(USER_ID, N);

            assertThat(result).extracting(ProductResponseDto::getUuid).containsExactly(uuid.toString());
        }

        @Test
        @DisplayName("both Gorse tiers fail → falls back to findBestSellers")
        void bothGorseTiersFail_fallsBackToBestSellers() {
            final ProductEntity product = productWithUuid(UUID.randomUUID());
            when(valueOperations.get(any())).thenReturn(null);
            when(gorseClient.getRecommendations(USER_ID, N)).thenThrow(new RuntimeException("Gorse down"));
            when(gorseClient.getLatestItems(N)).thenThrow(new RuntimeException("Gorse down"));
            when(productRepository.findBestSellers(PageRequest.of(0, N))).thenReturn(new PageImpl<>(List.of(product)));
            when(productMapper.mapFromEntityToResponseDto(product)).thenReturn(responseDtoFor(product));

            final List<ProductResponseDto> result = service.getRecommendedProducts(USER_ID, N);

            assertThat(result).extracting(ProductResponseDto::getUuid).containsExactly(product.getUuid().toString());
        }

        @Test
        @DisplayName("preserves Gorse's ranking order even when the repository returns rows in a different order")
        void preservesGorseOrder() {
            final UUID uuid1 = UUID.randomUUID();
            final UUID uuid2 = UUID.randomUUID();
            final ProductEntity p1 = productWithUuid(uuid1);
            final ProductEntity p2 = productWithUuid(uuid2);
            when(valueOperations.get(any())).thenReturn(null);
            // Gorse ranks uuid2 first, but the repository (SQL IN, no ORDER BY) returns p1 before p2.
            when(gorseClient.getRecommendations(USER_ID, N)).thenReturn(List.of(uuid2.toString(), uuid1.toString()));
            when(productRepository.findAllByUuidIn(List.of(uuid2, uuid1))).thenReturn(List.of(p1, p2));
            when(productMapper.mapFromEntityToResponseDto(p1)).thenReturn(responseDtoFor(p1));
            when(productMapper.mapFromEntityToResponseDto(p2)).thenReturn(responseDtoFor(p2));

            final List<ProductResponseDto> result = service.getRecommendedProducts(USER_ID, N);

            assertThat(result).extracting(ProductResponseDto::getUuid)
                    .containsExactly(uuid2.toString(), uuid1.toString());
        }

        @Test
        @DisplayName("a Gorse item id with no matching product (deleted since sync) is silently skipped")
        void unmatchedItemId_isSkipped() {
            final UUID uuid1 = UUID.randomUUID();
            final UUID uuid2 = UUID.randomUUID();
            final ProductEntity p1 = productWithUuid(uuid1);
            when(valueOperations.get(any())).thenReturn(null);
            when(gorseClient.getRecommendations(USER_ID, N)).thenReturn(List.of(uuid1.toString(), uuid2.toString()));
            // uuid2 no longer exists in MySQL — repository only returns p1.
            when(productRepository.findAllByUuidIn(List.of(uuid1, uuid2))).thenReturn(List.of(p1));
            when(productMapper.mapFromEntityToResponseDto(p1)).thenReturn(responseDtoFor(p1));

            final List<ProductResponseDto> result = service.getRecommendedProducts(USER_ID, N);

            assertThat(result).extracting(ProductResponseDto::getUuid).containsExactly(uuid1.toString());
        }
    }
}
