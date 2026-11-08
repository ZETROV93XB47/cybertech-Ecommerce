package com.novatech.cybertech.integration.review;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.OrderItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ReviewEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ReviewRepository;
import com.novatech.cybertech.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which order statuses let the buyer review a product: {@code GET /review/reviewable} and
 * {@code POST /review/create} share the same rule. A paid order stays reviewable while it is
 * prepared (AWAITING_SHIPPING); unpaid, canceled, refunded and returned orders are not.
 * Rating-only reviews (no comment) skip moderation, so no moderation stub is needed.
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ReviewableOrderFlowIT — reviewable order statuses")
class ReviewableOrderFlowIT {

    private static final String REVIEWABLE_ENDPOINT = "/api/v1/services/review/reviewable";
    private static final String CREATE_ENDPOINT = "/api/v1/services/review/create";
    private static final String MY_REVIEWS_ENDPOINT = "/api/v1/services/review/mine";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private String keycloakId;
    private UUID productUuid;

    @BeforeEach
    void seed() {
        testDataCleaner.wipe();
        keycloakId = "kc-reviewer-" + UUID.randomUUID();
    }

    /** One order of the caller, in {@code status}, holding one line of a freshly saved product. */
    private UUID seedOrder(final OrderStatus status) {
        return transactionTemplate.execute(tx -> {
            final UserEntity user = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                    .keycloakId(keycloakId).email("reviewer+" + UUID.randomUUID() + "@example.com").build());
            final ProductEntity product = productRepository.save(ProductEntityBuilder.aValidProductBuilder()
                    .name("Montre " + UUID.randomUUID()).category("SMARTWATCH").build());
            productUuid = product.getUuid();

            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user)
                    .status(status)
                    .orderItemEntities(new ArrayList<>())
                    .build();
            final OrderItemEntity item = OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .productEntity(product)
                    .orderEntity(order)
                    .build();
            order.getOrderItemEntities().add(item);
            return orderRepository.save(order).getUuid();
        });
    }

    private byte[] ratingOnlyReview(final UUID orderUuid) {
        return objectMapper.writeValueAsBytes(ReviewCreateRequestDto.builder()
                .orderUuid(orderUuid)
                .productUuid(productUuid)
                .rating(5)
                .build());
    }

    @Test
    @DisplayName("AWAITING_SHIPPING: the product is listed as reviewable and the review is created (201)")
    void awaitingShippingOrderIsReviewable() throws Exception {
        final UUID orderUuid = seedOrder(OrderStatus.AWAITING_SHIPPING);

        mockMvc.perform(get(REVIEWABLE_ENDPOINT).with(jwtUser(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productUuid").value(productUuid.toString()))
                .andExpect(jsonPath("$[0].orderUuid").value(orderUuid.toString()));

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ratingOnlyReview(orderUuid)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));

        // Reviewed: no longer listed.
        mockMvc.perform(get(REVIEWABLE_ENDPOINT).with(jwtUser(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("second review of the same product -> 409 REVIEW_ALREADY_EXISTS, only the first one is stored")
    void secondReviewOfTheSameProductIsRejected() throws Exception {
        final UUID orderUuid = seedOrder(OrderStatus.DELIVERED);

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ratingOnlyReview(orderUuid)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ratingOnlyReview(orderUuid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REVIEW_ALREADY_EXISTS"));

        mockMvc.perform(get(MY_REVIEWS_ENDPOINT).with(jwtUser(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("two simultaneous POST /review/create of the same product -> one 201, one 409, one review stored")
    void simultaneousReviewsOfTheSameProductStoreOne() throws Exception {
        final UUID orderUuid = seedOrder(OrderStatus.DELIVERED);
        final byte[] body = ratingOnlyReview(orderUuid);
        final CountDownLatch start = new CountDownLatch(1);
        final Callable<Integer> create = () -> {
            start.await();
            return mockMvc.perform(post(CREATE_ENDPOINT)
                            .with(jwtUser(keycloakId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
        };

        final List<Integer> statuses;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            final Future<Integer> first = pool.submit(create);
            final Future<Integer> second = pool.submit(create);
            start.countDown();
            statuses = List.of(first.get(), second.get());
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(reviewRepository.existsByUserEntity_KeycloakIdAndProductEntity_Uuid(keycloakId, productUuid)).isTrue();
        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("uk_review_user_product: the database itself refuses a second review of the same product by the same user")
    void databaseRefusesASecondReviewOfTheSameProduct() {
        seedOrder(OrderStatus.DELIVERED);
        insertReviewDirectly();

        // Straight to the repository: no service pre-check, only the constraint stands in the way.
        assertThatThrownBy(this::insertReviewDirectly).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    private void insertReviewDirectly() {
        transactionTemplate.executeWithoutResult(tx -> reviewRepository.saveAndFlush(ReviewEntityBuilder.aValidReviewBuilder()
                .userEntity(userRepository.findByKeycloakId(keycloakId).orElseThrow())
                .productEntity(productRepository.findByUuid(productUuid).orElseThrow())
                .build()));
    }

    @ParameterizedTest(name = "{0}: not listed, create -> 409 ORDER_NOT_REVIEWABLE")
    @EnumSource(value = OrderStatus.class, names = {"AWAITING_PAYMENT", "PAYMENT_FAILED", "CANCELED", "REFUNDED", "RETURNED"})
    void notReviewableStatuses(final OrderStatus status) throws Exception {
        final UUID orderUuid = seedOrder(status);

        mockMvc.perform(get(REVIEWABLE_ENDPOINT).with(jwtUser(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ratingOnlyReview(orderUuid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_REVIEWABLE"));
    }
}
