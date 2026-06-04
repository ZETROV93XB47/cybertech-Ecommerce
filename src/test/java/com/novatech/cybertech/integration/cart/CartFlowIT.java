package com.novatech.cybertech.integration.cart;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.JwtTestUtils;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.repositories.CartRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.CartCacheHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end cart flow against real Testcontainers (MySQL, Redis, Mongo, Elasticsearch).
 *
 * <p>Each test wipes MySQL via {@link TestDataCleaner} and Redis cart keys via {@link RedisTemplate}
 * to keep the environment hermetic. Persistence is asserted via JPA repositories; cache state is
 * asserted via {@link CartCacheHelper#getRaw(String)}.
 *
 * <p>Bug-pin policy: original BUG numbers preserved (BUG-008, BUG-028, BUG-160, BUG-161).
 * New findings would consume BUG-510..BUG-519 — none filed by this run.
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("CartFlowIT — full /cart/* surface against real containers")
class CartFlowIT {

    private static final String CART_ADD_ENDPOINT = "/api/v1/services/cart/add";
    private static final String CART_GET_ENDPOINT = "/api/v1/services/cart/get";
    private static final String CART_GET_BY_UUID_ENDPOINT = "/api/v1/services/cart/get/{cartUuid}";
    private static final String CART_DELETE_BY_UUID_ENDPOINT = "/api/v1/services/cart/delete/{cartUuid}";
    private static final String CART_CLEAR_ENDPOINT = "/api/v1/services/cart/clear";
    private static final String CART_DECREASE_ENDPOINT = "/api/v1/services/cart/decreaseQuantity";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartCacheHelper cartCacheHelper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TestDataCleaner testDataCleaner;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UserEntity seededUser;
    private ProductEntity seededProduct;
    private String keycloakId;

    @BeforeEach
    void seed() {
        // 1) Wipe MySQL
        testDataCleaner.wipe();

        // 2) Wipe any leftover Redis cart keys (lock + cart)
        wipeRedisCartKeys();

        // 3) Seed user (with stable Keycloak id) + a product with stock=10
        keycloakId = "kc-it-" + UUID.randomUUID();

        seededUser = userRepository.save(
                UserEntityBuilder.aValidUserBuilder()
                        .keycloakId(keycloakId)
                        .email("cart-it+" + UUID.randomUUID() + "@example.com")
                        .build()
        );

        seededProduct = productRepository.save(
                ProductEntityBuilder.aValidProductBuilder()
                        .name("CartFlowIT Product")
                        .stock(10)
                        .reservedStock(0)
                        .build()
        );
    }

    @AfterEach
    void cleanup() {
        wipeRedisCartKeys();
    }

    private void wipeRedisCartKeys() {
        // The cache helper uses the literal "cart::" + keycloakId pattern.
        // Use SCAN-style keys() with a wildcard; Redis containers under test are tiny so safe.
        try {
            var cartKeys = redisTemplate.keys("cart::*");
            if (cartKeys != null && !cartKeys.isEmpty()) {
                redisTemplate.delete(cartKeys);
            }
            var lockKeys = redisTemplate.keys("lock:cart:*");
            if (lockKeys != null && !lockKeys.isEmpty()) {
                redisTemplate.delete(lockKeys);
            }
        } catch (RuntimeException ignored) {
            // Best-effort — never block the test on cache wipe
        }
    }

    // ----------------------------------------------------------------------------------
    // 1. Add-item happy path: POST /cart/add → DB + cache assertions
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("addItem happy path — persists in MySQL and writes to Redis cache")
    void addItemHappyPath_persistsInDbAndCachesInRedis() throws Exception {
        CartCreateRequestDto request = CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(List.of(
                        CartItemAddRequestDto.builder()
                                .productUuid(seededProduct.getUuid())
                                .quantity(2)
                                .build()))
                .build();

        mockMvc.perform(post(CART_ADD_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(keycloakId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].productUuid").value(seededProduct.getUuid().toString()));

        // DB assertion: a CartEntity exists with one CartItem qty=2
        transactionTemplate.executeWithoutResult(tx -> {
            UserEntity reloaded = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            CartEntity cart = reloaded.getCartEntity();
            assertThat(cart).as("cart should be created on first add").isNotNull();
            assertThat(cart.getCartItems()).hasSize(1);
            assertThat(cart.getCartItems().get(0).getQuantity()).isEqualTo(2);
            assertThat(cart.getCartItems().get(0).getProductEntity().getUuid()).isEqualTo(seededProduct.getUuid());
        });

        // Cache assertion: cart was written under the keycloakId key
        CartResponseDto cached = cartCacheHelper.getRaw(keycloakId);
        assertThat(cached).as("cart should be cached after add").isNotNull();
        assertThat(cached.getItems()).hasSize(1);
        assertThat(cached.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    // ----------------------------------------------------------------------------------
    // 2. Quantity update via decreaseQuantity → DB reflects new total
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("decreaseQuantity reduces line qty in MySQL")
    void decreaseQuantityReducesLineInDb() throws Exception {
        // First add 5
        addItems(seededProduct.getUuid(), 5);

        CartItemRemoveRequestDto decrease = CartItemRemoveRequestDto.builder()
                .productUuid(seededProduct.getUuid())
                .quantity(2)
                .build();

        mockMvc.perform(delete(CART_DECREASE_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(keycloakId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decrease)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3));

        transactionTemplate.executeWithoutResult(tx -> {
            UserEntity reloaded = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            CartEntity cart = reloaded.getCartEntity();
            assertThat(cart.getCartItems()).hasSize(1);
            assertThat(cart.getCartItems().get(0).getQuantity()).isEqualTo(3);
        });
    }

    // ----------------------------------------------------------------------------------
    // 3. Clear cart → empty in DB
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("clearCart empties the line items in MySQL")
    void clearCartEmptiesLinesInDb() throws Exception {
        addItems(seededProduct.getUuid(), 1);

        mockMvc.perform(delete(CART_CLEAR_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(keycloakId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        transactionTemplate.executeWithoutResult(tx -> {
            UserEntity reloaded = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            CartEntity cart = reloaded.getCartEntity();
            assertThat(cart).isNotNull();
            assertThat(cart.getCartItems()).isEmpty();
        });
    }

    // ----------------------------------------------------------------------------------
    // 4. Out-of-stock batch (3rd item OOS). Per F2 fix BUG-008 the handler maps to 409.
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("batch with OOS third item — F2 BUG-008 handler returns 409")
    void outOfStockThirdItemReturns409() throws Exception {
        // 2 already-stocked products + 1 OOS product
        ProductEntity p2 = productRepository.save(
                ProductEntityBuilder.aValidProductBuilder().name("p2").stock(10).reservedStock(0).build());
        ProductEntity oos = productRepository.save(
                ProductEntityBuilder.aValidProductBuilder().name("oos").stock(1).reservedStock(1).build());

        CartCreateRequestDto request = CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(List.of(
                        CartItemAddRequestDto.builder().productUuid(seededProduct.getUuid()).quantity(1).build(),
                        CartItemAddRequestDto.builder().productUuid(p2.getUuid()).quantity(1).build(),
                        CartItemAddRequestDto.builder().productUuid(oos.getUuid()).quantity(5).build()))
                .build();

        mockMvc.perform(post(CART_ADD_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(keycloakId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatusCode").value(409))
                .andExpect(jsonPath("$.errorCodeType").value("FUNCTIONAL"));
    }

    // ----------------------------------------------------------------------------------
    // 5. Nested @Valid propagation (BUG-028 closed by F2): items.quantity = -1 → 400
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("nested negative quantity — F2 BUG-028 nested @Valid returns 400")
    void nestedNegativeQuantityReturns400() throws Exception {
        CartCreateRequestDto request = CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(List.of(
                        CartItemAddRequestDto.builder()
                                .productUuid(seededProduct.getUuid())
                                .quantity(-1)
                                .build()))
                .build();

        mockMvc.perform(post(CART_ADD_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(keycloakId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatusCode").value(400));
    }

    // ----------------------------------------------------------------------------------
    // 6. Concurrency: 2 threads add 2 each via CountDownLatch → final qty MUST equal 4.
    //    Sister SA-W2.2 + SA-W3.4 refuted F1's claim of fix; pin via @Disabled if surfaces.
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("BUG-160 (CLOSED): concurrent /cart/add — final qty MUST sum (no race)")
    void concurrentAddsFromTwoThreadsShouldSumNotRace() throws Exception {
        final int threads = 2;
        final int qtyPerThread = 2;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicInteger errorCount = new AtomicInteger();
        final ExecutorService pool = Executors.newFixedThreadPool(threads);

        Runnable addOne = () -> {
            try {
                start.await();
                CartCreateRequestDto req = CartCreateRequestDto.builder()
                        .cartItemAddRequestDtos(List.of(
                                CartItemAddRequestDto.builder()
                                        .productUuid(seededProduct.getUuid())
                                        .quantity(qtyPerThread)
                                        .build()))
                        .build();
                mockMvc.perform(post(CART_ADD_ENDPOINT)
                                .with(JwtTestUtils.jwtUser(keycloakId))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated());
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                done.countDown();
            }
        };

        for (int i = 0; i < threads; i++) {
            pool.submit(addOne);
        }
        start.countDown();
        boolean completed = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(completed).as("both threads should finish in time").isTrue();
        assertThat(errorCount.get()).as("no thread should have errored").isZero();

        // PIN: the correct final quantity is the sum of both thread contributions.
        // BUG-160 surfaces when the read-modify-write inside CartServiceImp.addItemsToCart
        // races and the second thread overwrites the first.
        transactionTemplate.executeWithoutResult(tx -> {
            UserEntity reloaded = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            CartEntity cart = reloaded.getCartEntity();
            assertThat(cart).isNotNull();
            assertThat(cart.getCartItems()).hasSize(1);
            assertThat(cart.getCartItems().get(0).getQuantity())
                    .as("final qty must equal sum of concurrent additions (BUG-160)")
                    .isEqualTo(threads * qtyPerThread);
        });
    }

    // ----------------------------------------------------------------------------------
    // 6b. Higher-contention concurrency: 5 threads add 2 each → final qty MUST equal 10.
    //     Stresses the Redis-lock + two-method-split serialization AFTER the DB pessimistic
    //     lock (layer 3) was removed — proving the Redis lock alone serialises the RMW.
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("BUG-160 (CLOSED): 5 concurrent /cart/add — final qty MUST sum to 10 (Redis lock only)")
    void concurrentAddsFromFiveThreadsShouldSumNotRace() throws Exception {
        final int threads = 5;
        final int qtyPerThread = 2;
        // Ensure stock can absorb the full concurrent demand so the only thing under test is the
        // lost-update race, not the stock guard.
        ProductEntity richStock = productRepository.save(
                ProductEntityBuilder.aValidProductBuilder().name("rich").stock(100).reservedStock(0).build());

        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicInteger errorCount = new AtomicInteger();
        final ExecutorService pool = Executors.newFixedThreadPool(threads);

        Runnable addOne = () -> {
            try {
                start.await();
                CartCreateRequestDto req = CartCreateRequestDto.builder()
                        .cartItemAddRequestDtos(List.of(
                                CartItemAddRequestDto.builder()
                                        .productUuid(richStock.getUuid())
                                        .quantity(qtyPerThread)
                                        .build()))
                        .build();
                mockMvc.perform(post(CART_ADD_ENDPOINT)
                                .with(JwtTestUtils.jwtUser(keycloakId))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isCreated());
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                done.countDown();
            }
        };

        for (int i = 0; i < threads; i++) {
            pool.submit(addOne);
        }
        start.countDown();
        boolean completed = done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(completed).as("all threads should finish in time").isTrue();
        assertThat(errorCount.get()).as("no thread should have errored (no DIVE/500 without layer 3)").isZero();

        transactionTemplate.executeWithoutResult(tx -> {
            UserEntity reloaded = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            CartEntity cart = reloaded.getCartEntity();
            assertThat(cart).isNotNull();
            assertThat(cart.getCartItems()).hasSize(1);
            assertThat(cart.getCartItems().get(0).getQuantity())
                    .as("final qty must equal sum of all 5 concurrent additions (Redis lock serialises RMW)")
                    .isEqualTo(threads * qtyPerThread);
        });
    }

    // ----------------------------------------------------------------------------------
    // 7. Delete-by-uuid endpoint audit — verifies path binds correctly post-F2 BUG-027 fix.
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("DELETE /cart/delete/{cartUuid} binds path variable post-F2 BUG-027 fix")
    void deleteByCartUuidBindsPathVariable() throws Exception {
        addItems(seededProduct.getUuid(), 1);
        UUID cartUuid = transactionTemplate.execute(tx -> {
            UserEntity reloaded = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            return reloaded.getCartEntity().getUuid();
        });
        assertThat(cartUuid).isNotNull();

        mockMvc.perform(delete(CART_DELETE_BY_UUID_ENDPOINT, cartUuid)
                        .with(JwtTestUtils.jwtUser(keycloakId))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // DB should no longer have the cart
        boolean stillExists = cartRepository.findByUuid(cartUuid).isPresent();
        assertThat(stillExists).as("cart row should be deleted").isFalse();
    }

    // ----------------------------------------------------------------------------------
    // 8. Pagination surface — /cart/get returns the user's cart shape (no Page<T> here,
    //    but the assertion documents the JSON response surface for consumers).
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("GET /cart/get returns the populated cart shape for the JWT subject")
    void getMyCartReturnsCartShape() throws Exception {
        addItems(seededProduct.getUuid(), 3);

        mockMvc.perform(get(CART_GET_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].productUuid").value(seededProduct.getUuid().toString()))
                .andExpect(jsonPath("$.totalPrice").exists());
    }

    // ----------------------------------------------------------------------------------
    // 9a. BUG-161 (CLOSED): GET /cart/get/{cartUuid} now rejects non-owners with 403.
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("BUG-161 (CLOSED) — GET /cart/get/{cartUuid} returns 403 for non-owner")
    void idorOnGetByCartUuid_returns403() throws Exception {
        // Seed user A's cart
        addItems(seededProduct.getUuid(), 1);
        UUID userACartUuid = transactionTemplate.execute(tx -> {
            UserEntity reloaded = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            return reloaded.getCartEntity().getUuid();
        });

        // User B (different keycloakId, no cart of their own)
        String userBKeycloakId = "kc-it-userB-" + UUID.randomUUID();
        userRepository.save(
                UserEntityBuilder.aValidUserBuilder()
                        .keycloakId(userBKeycloakId)
                        .email("userB+" + UUID.randomUUID() + "@example.com")
                        .build()
        );

        // BUG-161 (CLOSED): user B is now blocked from reading user A's cart -> 403.
        mockMvc.perform(get(CART_GET_BY_UUID_ENDPOINT, userACartUuid)
                        .with(JwtTestUtils.jwtUser(userBKeycloakId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------------------------
    // 9b. BUG-161 (CLOSED): DELETE /cart/delete/{cartUuid} returns 403 for non-owner.
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("BUG-161 (CLOSED) — DELETE /cart/delete/{cartUuid} returns 403 for non-owner")
    void idorOnDeleteByCartUuidReturnsForbidden() throws Exception {
        addItems(seededProduct.getUuid(), 1);
        UUID userACartUuid = transactionTemplate.execute(tx -> {
            UserEntity reloaded = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            return reloaded.getCartEntity().getUuid();
        });

        String userBKeycloakId = "kc-it-userB-" + UUID.randomUUID();
        userRepository.save(
                UserEntityBuilder.aValidUserBuilder()
                        .keycloakId(userBKeycloakId)
                        .email("userB+" + UUID.randomUUID() + "@example.com")
                        .build()
        );

        mockMvc.perform(delete(CART_DELETE_BY_UUID_ENDPOINT, userACartUuid)
                        .with(JwtTestUtils.jwtUser(userBKeycloakId))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------------------------
    private MvcResult addItems(UUID productUuid, int quantity) throws Exception {
        CartCreateRequestDto request = CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(new ArrayList<>(List.of(
                        CartItemAddRequestDto.builder()
                                .productUuid(productUuid)
                                .quantity(quantity)
                                .build())))
                .build();
        return mockMvc.perform(post(CART_ADD_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(keycloakId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
