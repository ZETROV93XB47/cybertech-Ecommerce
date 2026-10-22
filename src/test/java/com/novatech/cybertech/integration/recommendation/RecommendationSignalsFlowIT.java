package com.novatech.cybertech.integration.recommendation;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.batch.task.GorseSyncTasklet;
import com.novatech.cybertech.clients.GorseClient;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.OrderItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.fixtures.support.stubs.ModerationStub;
import com.novatech.cybertech.fixtures.support.stubs.StripeEventBuilder;
import com.novatech.cybertech.mappers.gorse.GorseMapper;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserEventRepository;
import com.novatech.cybertech.repositories.UserRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recommendation signals produced by the backend, end to end: a logged-in user adds to the cart,
 * to the wishlist, has an order paid by the (simulated) Stripe webhook and posts a 5-star review;
 * the matching {@link UserEvent}s land in MongoDB, then {@link GorseSyncTasklet} sends them to a
 * fake Gorse ({@link MockRestServiceServer}) as feedback with the right type, user and item.
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ModerationStub.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "stripe.webhook.secret=" + RecommendationSignalsFlowIT.WEBHOOK_SECRET)
@DisplayName("RecommendationSignalsFlowIT — backend-produced UserEvents, then Gorse feedback")
class RecommendationSignalsFlowIT {

    static final String WEBHOOK_SECRET = "whsec_test_recommendation_signals";

    private static final String CART_ADD_ENDPOINT = "/api/v1/services/cart/add";
    private static final String WISHLIST_ADD_ENDPOINT = "/api/v1/services/wishlist/add/";
    private static final String REVIEW_CREATE_ENDPOINT = "/api/v1/services/review/create";
    private static final String STRIPE_WEBHOOK_ENDPOINT = "/api/v1/webhooks/stripe";
    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";
    private static final String GORSE_URL = "http://gorse.test";
    private static final Duration ASYNC_WAIT = Duration.ofSeconds(20);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentAttemptRepository paymentAttemptRepository;
    @Autowired private UserEventRepository userEventRepository;
    @Autowired private GorseMapper gorseMapper;
    @Autowired private TestDataCleaner testDataCleaner;
    @Autowired private TransactionTemplate transactionTemplate;

    private String keycloakId;
    private ProductEntity phone;
    private ProductEntity watch;

    @BeforeEach
    void seed() {
        testDataCleaner.wipe();
        userEventRepository.deleteAll();
        keycloakId = "kc-reco-" + UUID.randomUUID();
        userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .keycloakId(keycloakId).email("reco+" + UUID.randomUUID() + "@example.com").build());
        phone = productRepository.save(ProductEntityBuilder.aValidProductBuilder()
                .name("Phone " + UUID.randomUUID()).category("SMARTPHONE").stock(20).build());
        watch = productRepository.save(ProductEntityBuilder.aValidProductBuilder()
                .name("Watch " + UUID.randomUUID()).category("SMARTWATCH").stock(20).build());
    }

    @Test
    @DisplayName("cart, wishlist, paid order and 5-star review -> UserEvents in MongoDB -> Gorse feedback")
    void loggedInJourneyFeedsGorse() throws Exception {
        // 1) Add to cart
        mockMvc.perform(post(CART_ADD_ENDPOINT).with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(CartCreateRequestDto.builder()
                                .cartItemAddRequestDtos(new ArrayList<>(List.of(CartItemAddRequestDto.builder()
                                        .productUuid(phone.getUuid()).quantity(1).build())))
                                .build())))
                .andExpect(status().isCreated());

        // 2) Wishlist
        mockMvc.perform(post(WISHLIST_ADD_ENDPOINT + watch.getUuid()).with(jwtUser(keycloakId)))
                .andExpect(status().isCreated());

        // 3) Order paid by the Stripe webhook
        final UUID orderUuid = seedAwaitingPaymentOrder(phone);
        final String paymentIntentId = seedPayment(orderUuid);
        sendPaymentSucceededWebhook(paymentIntentId, orderUuid);
        Awaitility.await().atMost(ASYNC_WAIT).untilAsserted(() ->
                assertThat(orderRepository.findByUuid(orderUuid).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID));

        // 4) 5-star review with a (moderated, clean) comment
        mockMvc.perform(post(REVIEW_CREATE_ENDPOINT).with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(ReviewCreateRequestDto.builder()
                                .orderUuid(orderUuid).productUuid(phone.getUuid()).rating(5).comment("Excellent téléphone").build())))
                .andExpect(status().isCreated());

        Awaitility.await().atMost(ASYNC_WAIT).untilAsserted(() ->
                assertThat(userEventRepository.findByUserId(keycloakId))
                        .extracting(UserEvent::getEventType, UserEvent::getProductId)
                        .containsExactlyInAnyOrder(
                                tuple(UserEventType.ADD_TO_CART, phone.getUuid().toString()),
                                tuple(UserEventType.WISHLIST_ADD, watch.getUuid().toString()),
                                tuple(UserEventType.PURCHASE, phone.getUuid().toString()),
                                tuple(UserEventType.REVIEW_POSITIVE, phone.getUuid().toString())));

        final UserEvent purchase = userEventRepository.findByEventTypeAndUserId(UserEventType.PURCHASE, keycloakId).getFirst();
        assertThat(purchase.getSessionId()).isEqualTo("server");
        assertThat(purchase.getMetadata())
                .containsEntry("orderUuid", orderUuid.toString())
                .containsEntry("category", "SMARTPHONE")
                .containsKeys("brand", "price");

        // 5) Gorse sync against a fake Gorse
        final List<Map<String, Object>> feedback = runGorseSync();

        assertThat(feedback)
                .filteredOn(f -> keycloakId.equals(f.get("UserId")))
                .extracting(f -> f.get("FeedbackType"), f -> f.get("ItemId"))
                .containsExactlyInAnyOrder(
                        tuple("ADD_TO_CART", phone.getUuid().toString()),
                        tuple("WISHLIST_ADD", watch.getUuid().toString()),
                        tuple("PURCHASE", phone.getUuid().toString()),
                        tuple("REVIEW_POSITIVE", phone.getUuid().toString()));
    }

    @Test
    @DisplayName("modified paid order paid again (complement): PURCHASE only for the new product, never twice for the same one")
    void complementPaymentDoesNotDuplicatePurchase() throws Exception {
        final UUID orderUuid = seedAwaitingPaymentOrder(phone);
        sendPaymentSucceededWebhook(seedPayment(orderUuid), orderUuid);
        Awaitility.await().atMost(ASYNC_WAIT).untilAsserted(() ->
                assertThat(userEventRepository.findByEventTypeAndUserId(UserEventType.PURCHASE, keycloakId)).hasSize(1));

        // The paid order is modified (a watch added) and goes back to AWAITING_PAYMENT for the complement.
        transactionTemplate.executeWithoutResult(tx -> {
            final OrderEntity order = orderRepository.findByUuid(orderUuid).orElseThrow();
            order.getOrderItemEntities().add(OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .productEntity(productRepository.findByUuid(watch.getUuid()).orElseThrow()).orderEntity(order).build());
            order.setStatus(OrderStatus.AWAITING_PAYMENT);
            orderRepository.save(order);
        });
        sendPaymentSucceededWebhook(seedPayment(orderUuid), orderUuid);

        Awaitility.await().atMost(ASYNC_WAIT).untilAsserted(() ->
                assertThat(userEventRepository.findByEventTypeAndUserId(UserEventType.PURCHASE, keycloakId))
                        .extracting(UserEvent::getProductId)
                        .containsExactlyInAnyOrder(phone.getUuid().toString(), watch.getUuid().toString()));
        // Still no duplicate once everything has settled.
        Thread.sleep(1_000);
        assertThat(userEventRepository.findByEventTypeAndUserId(UserEventType.PURCHASE, keycloakId)).hasSize(2);
    }

    // ---------------------------------------------------------------------------------

    private UUID seedAwaitingPaymentOrder(final ProductEntity product) {
        return transactionTemplate.execute(tx -> {
            final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow();
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user)
                    .status(OrderStatus.AWAITING_PAYMENT)
                    .orderItemEntities(new ArrayList<>())
                    .build();
            final OrderItemEntity item = OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .productEntity(productRepository.findByUuid(product.getUuid()).orElseThrow())
                    .orderEntity(order)
                    .build();
            order.getOrderItemEntities().add(item);
            return orderRepository.save(order).getUuid();
        });
    }

    private String seedPayment(final UUID orderUuid) {
        final String paymentIntentId = "pi_test_" + UUID.randomUUID().toString().replace("-", "");
        transactionTemplate.executeWithoutResult(tx -> paymentAttemptRepository.save(PaymentEntityBuilder.aValidPaymentBuilder()
                .orderEntity(orderRepository.findByUuid(orderUuid).orElseThrow())
                .stripePaymentID(paymentIntentId)
                .status(PaymentAttemptStatus.PROCESSING)
                .build()));
        return paymentIntentId;
    }

    private void sendPaymentSucceededWebhook(final String paymentIntentId, final UUID orderUuid) throws Exception {
        final String idempotencyKey = paymentAttemptRepository.findByStripePaymentID(paymentIntentId).orElseThrow().getIdempotencyKey();
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET,
                paymentIntentSucceededJson(paymentIntentId, orderUuid, idempotencyKey));
        mockMvc.perform(post(STRIPE_WEBHOOK_ENDPOINT)
                        .header(STRIPE_SIGNATURE_HEADER, signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());
    }

    /** Runs the real tasklet against a fake Gorse and returns the body of its PUT /api/feedback. */
    private List<Map<String, Object>> runGorseSync() throws Exception {
        final RestClient.Builder builder = RestClient.builder();
        final MockRestServiceServer gorse = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        final AtomicReference<List<Map<String, Object>>> feedback = new AtomicReference<>();
        gorse.expect(ExpectedCount.between(0, 1), requestTo(GORSE_URL + "/api/items")).andRespond(withSuccess());
        gorse.expect(ExpectedCount.between(0, 1), requestTo(GORSE_URL + "/api/users")).andRespond(withSuccess());
        gorse.expect(ExpectedCount.once(), requestTo(GORSE_URL + "/api/feedback"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(request -> feedback.set(objectMapper.readValue(((MockClientHttpRequest) request).getBodyAsString(),
                        new TypeReference<List<Map<String, Object>>>() {
                        })))
                .andRespond(withSuccess());

        final GorseClient gorseClient = new GorseClient(builder.build());
        ReflectionTestUtils.setField(gorseClient, "gorseApiUrl", GORSE_URL);
        final GorseSyncTasklet tasklet = new GorseSyncTasklet(productRepository, userRepository, userEventRepository, gorseClient, gorseMapper);
        ReflectionTestUtils.setField(tasklet, "lookbackMinutes", 20);

        tasklet.execute(mock(StepContribution.class), (GorseSyncTasklet.StepArguments) null);

        gorse.verify();
        return feedback.get();
    }

    private static String paymentIntentSucceededJson(final String paymentIntentId, final UUID orderUuid, final String idempotencyKey) {
        return """
                {
                  "id": "evt_%s",
                  "object": "event",
                  "api_version": "2024-04-10",
                  "created": %d,
                  "type": "payment_intent.succeeded",
                  "livemode": false,
                  "pending_webhooks": 0,
                  "request": {"id": null, "idempotency_key": null},
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "amount": 10000,
                      "amount_received": 10000,
                      "currency": "eur",
                      "status": "succeeded",
                      "metadata": {
                        "order_uuid": "%s",
                        "idempotency_key": "%s"
                      }
                    }
                  }
                }
                """.formatted(UUID.randomUUID().toString().replace("-", ""), System.currentTimeMillis() / 1000L,
                paymentIntentId, orderUuid, idempotencyKey);
    }
}
