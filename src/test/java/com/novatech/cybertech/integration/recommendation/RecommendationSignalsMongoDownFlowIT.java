package com.novatech.cybertech.integration.recommendation;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.OrderItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.fixtures.support.stubs.StripeEventBuilder;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserEventRepository;
import com.novatech.cybertech.repositories.UserRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MongoDB unreachable (every {@code UserEventRepository} write fails): adding to the cart and the
 * payment confirmation still succeed; the recommendation signal is attempted, logged and lost.
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "stripe.webhook.secret=" + RecommendationSignalsMongoDownFlowIT.WEBHOOK_SECRET)
@DisplayName("RecommendationSignalsMongoDownFlowIT — a MongoDB outage never fails the business action")
class RecommendationSignalsMongoDownFlowIT {

    static final String WEBHOOK_SECRET = "whsec_test_recommendation_mongo_down";

    private static final String CART_ADD_ENDPOINT = "/api/v1/services/cart/add";
    private static final String STRIPE_WEBHOOK_ENDPOINT = "/api/v1/webhooks/stripe";
    private static final Duration ASYNC_WAIT = Duration.ofSeconds(20);

    @MockitoBean
    private UserEventRepository userEventRepository;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentAttemptRepository paymentAttemptRepository;
    @Autowired private TestDataCleaner testDataCleaner;
    @Autowired private TransactionTemplate transactionTemplate;

    private String keycloakId;
    private ProductEntity phone;

    @BeforeEach
    void seed() {
        testDataCleaner.wipe();
        when(userEventRepository.save(any())).thenThrow(new DataAccessResourceFailureException("Timed out while waiting for a server (MongoDB down)"));
        keycloakId = "kc-mongo-down-" + UUID.randomUUID();
        userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .keycloakId(keycloakId).email("mongo-down+" + UUID.randomUUID() + "@example.com").build());
        phone = productRepository.save(ProductEntityBuilder.aValidProductBuilder()
                .name("Phone " + UUID.randomUUID()).category("SMARTPHONE").stock(20).build());
    }

    @Test
    @DisplayName("add to cart -> 201 with the line, the failed signal write is only logged")
    void cartAddSucceeds() throws Exception {
        mockMvc.perform(post(CART_ADD_ENDPOINT).with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(CartCreateRequestDto.builder()
                                .cartItemAddRequestDtos(new ArrayList<>(List.of(CartItemAddRequestDto.builder()
                                        .productUuid(phone.getUuid()).quantity(1).build())))
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].productUuid").value(phone.getUuid().toString()));

        verify(userEventRepository, timeout(ASYNC_WAIT.toMillis()).atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("payment confirmation -> order PAID despite the failed PURCHASE write")
    void paymentConfirmationSucceeds() throws Exception {
        final UUID orderUuid = transactionTemplate.execute(tx -> {
            final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user).status(OrderStatus.AWAITING_PAYMENT).orderItemEntities(new ArrayList<>()).build();
            final OrderItemEntity item = OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .productEntity(productRepository.findByUuid(phone.getUuid()).orElseThrow()).orderEntity(order).build();
            order.getOrderItemEntities().add(item);
            return orderRepository.save(order).getUuid();
        });
        final String paymentIntentId = "pi_test_" + UUID.randomUUID().toString().replace("-", "");
        final String idempotencyKey = "idem-" + UUID.randomUUID();
        transactionTemplate.executeWithoutResult(tx -> paymentAttemptRepository.save(PaymentEntityBuilder.aValidPaymentBuilder()
                .orderEntity(orderRepository.findByUuid(orderUuid).orElseThrow())
                .stripePaymentID(paymentIntentId).idempotencyKey(idempotencyKey)
                .status(PaymentAttemptStatus.PROCESSING).build()));

        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, """
                {"id": "evt_%s", "object": "event", "api_version": "2024-04-10", "created": %d,
                 "type": "payment_intent.succeeded", "livemode": false, "pending_webhooks": 0,
                 "request": {"id": null, "idempotency_key": null},
                 "data": {"object": {"id": "%s", "object": "payment_intent", "amount": 10000, "amount_received": 10000,
                   "currency": "eur", "status": "succeeded", "metadata": {"order_uuid": "%s", "idempotency_key": "%s"}}}}
                """.formatted(UUID.randomUUID().toString().replace("-", ""), System.currentTimeMillis() / 1000L,
                paymentIntentId, orderUuid, idempotencyKey));
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header("Stripe-Signature", signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());

        Awaitility.await().atMost(ASYNC_WAIT).untilAsserted(() ->
                assertThat(orderRepository.findByUuid(orderUuid).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID));
        verify(userEventRepository, timeout(ASYNC_WAIT.toMillis()).atLeastOnce()).save(any());
    }
}
