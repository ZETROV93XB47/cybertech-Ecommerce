package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.services.implementation.CartCacheHelperImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link CartCacheHelperImp}.
 *
 * <p>SA-W3.4 wave — covers cache key format ({@code cart::<userId>}), TTL jitter band,
 * sliding-TTL refresh on hit, and exception propagation. {@code @Value} fields are seeded
 * via {@link ReflectionTestUtils} since we don't bootstrap Spring.</p>
 */
@ExtendWith(MockitoExtension.class)
class CartCacheHelperImpTest {

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOperations;

    private CartCacheHelperImp helper;

    private static final int BASE_TTL = 60;
    private static final int JITTER_MAX = 10;

    @BeforeEach
    void setUp() {
        helper = new CartCacheHelperImp(redisTemplate);
        ReflectionTestUtils.setField(helper, "baseTtlSeconds", BASE_TTL);
        ReflectionTestUtils.setField(helper, "jitterMaxSeconds", JITTER_MAX);
    }

    // =================================================================
    @Nested
    @DisplayName("getRaw")
    class GetRaw {

        @Test
        @DisplayName("cache hit returns cart from valueOps using key cart::<userId>")
        void cacheHit_returnsCart() {
            final String userId = "user-1";
            final CartResponseDto cached = new CartResponseDto(UUID.randomUUID(), UUID.randomUUID(), null, null);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("cart::user-1")).thenReturn(cached);

            final CartResponseDto result = helper.getRaw(userId);

            assertThat(result).isSameAs(cached);
            verify(valueOperations).get("cart::user-1");
        }

        @Test
        @DisplayName("cache miss returns null")
        void cacheMiss_returnsNull() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("cart::missing")).thenReturn(null);

            final CartResponseDto result = helper.getRaw("missing");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("propagates Redis exceptions")
        void propagatesRedisException() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis-down"));

            assertThatThrownBy(() -> helper.getRaw("user-2"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("redis-down");
        }

        @Test
        @DisplayName("getRaw does NOT refresh TTL")
        void getRaw_doesNotRefreshTtl() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(new CartResponseDto());

            helper.getRaw("user-3");

            verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("uses per-user cache key cart::<userId>")
        void getRaw_usesPerUserKey() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            helper.getRaw("alice");
            helper.getRaw("bob");

            verify(valueOperations).get("cart::alice");
            verify(valueOperations).get("cart::bob");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("putWithJitter")
    class PutWithJitter {

        @Test
        @DisplayName("writes to cache::<userId> via valueOps#set")
        void putWithJitter_writesToCorrectKey() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            final CartResponseDto cart = new CartResponseDto(UUID.randomUUID(), UUID.randomUUID(), null, null);

            helper.putWithJitter("user-7", cart);

            verify(valueOperations).set(eq("cart::user-7"), eq(cart), any(Duration.class));
        }

        @Test
        @DisplayName("TTL stays within base ± jitter band over 25 iterations")
        void putWithJitter_ttlWithinJitterBand() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            final CartResponseDto cart = new CartResponseDto();

            for (int i = 0; i < 25; i++) {
                helper.putWithJitter("user-band-" + i, cart);
            }

            final ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOperations, times(25)).set(anyString(), any(), ttlCaptor.capture());

            for (Duration d : ttlCaptor.getAllValues()) {
                long secs = d.getSeconds();
                assertThat(secs)
                        .as("TTL %ds must be within [%d, %d]", secs, BASE_TTL - JITTER_MAX, BASE_TTL + JITTER_MAX)
                        .isBetween((long) (BASE_TTL - JITTER_MAX), (long) (BASE_TTL + JITTER_MAX));
            }
        }

        @Test
        @DisplayName("when jitterMaxSeconds<=0, TTL equals baseTtlSeconds exactly")
        void putWithJitter_zeroJitterIsExactBase() {
            ReflectionTestUtils.setField(helper, "jitterMaxSeconds", 0);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            helper.putWithJitter("user-8", new CartResponseDto());

            final ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOperations).set(anyString(), any(), ttlCaptor.capture());
            assertThat(ttlCaptor.getValue().getSeconds()).isEqualTo(BASE_TTL);
        }

        @Test
        @DisplayName("TTL is at least 1s even when base+jitter could go to 0")
        void putWithJitter_neverNonPositive() {
            ReflectionTestUtils.setField(helper, "baseTtlSeconds", 1);
            ReflectionTestUtils.setField(helper, "jitterMaxSeconds", 5);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            for (int i = 0; i < 50; i++) {
                helper.putWithJitter("guard-" + i, new CartResponseDto());
            }

            final ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOperations, times(50)).set(anyString(), any(), ttlCaptor.capture());

            for (Duration d : ttlCaptor.getAllValues()) {
                assertThat(d.getSeconds()).isGreaterThanOrEqualTo(1L);
            }
        }
    }

    // =================================================================
    @Nested
    @DisplayName("refreshTtlWithJitter")
    class RefreshTtl {

        @Test
        @DisplayName("calls redisTemplate.expire on cart::<userId>")
        void refreshTtl_callsExpireOnCartKey() {
            helper.refreshTtlWithJitter("user-9");

            verify(redisTemplate).expire(eq("cart::user-9"), any(Duration.class));
        }

        @Test
        @DisplayName("refresh TTL stays within jitter band over 25 iterations")
        void refreshTtl_withinJitterBand() {
            for (int i = 0; i < 25; i++) {
                helper.refreshTtlWithJitter("u-" + i);
            }

            final ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(redisTemplate, times(25)).expire(anyString(), ttlCaptor.capture());

            for (Duration d : ttlCaptor.getAllValues()) {
                long secs = d.getSeconds();
                assertThat(secs).isBetween((long) (BASE_TTL - JITTER_MAX), (long) (BASE_TTL + JITTER_MAX));
            }
        }
    }

    // =================================================================
    @Nested
    @DisplayName("acquireLock / releaseLock")
    class Locking {

        @Test
        @DisplayName("acquireLock returns token when setIfAbsent succeeds, on key lock:cart:<userId>")
        void acquireLock_success_returnsToken() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:cart:user-A"), anyString(), any(Duration.class)))
                    .thenReturn(true);

            final String token = helper.acquireLock("user-A");

            assertThat(token).isNotNull();
            // Token should be a valid UUID string
            assertThat(UUID.fromString(token)).isNotNull();
        }

        @Test
        @DisplayName("acquireLock returns null when setIfAbsent returns false")
        void acquireLock_failure_returnsNull() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

            final String token = helper.acquireLock("user-B");

            assertThat(token).isNull();
        }

        @Test
        @DisplayName("acquireLock returns null when setIfAbsent returns null (no Boolean)")
        void acquireLock_nullResponse_returnsNull() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(null);

            final String token = helper.acquireLock("user-C");

            assertThat(token).isNull();
        }

        @Test
        @DisplayName("releaseLock executes the unlock Lua script callback against Redis")
        void releaseLock_executesScript() {
            // Just ensure invocation reaches redisTemplate.execute(...)
            helper.releaseLock("user-D", "tok-D");

            verify(redisTemplate).execute(any(org.springframework.data.redis.core.RedisCallback.class));
        }
    }
}
