package com.novatech.cybertech.integration.review;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.OrderItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ReviewRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review comments against a simulated moderation service (a JDK {@link HttpServer} that only
 * serves {@value #ANALYZE_PATH}, like the Flask app), through the real {@code CommentModerationClient},
 * Resilience4j retry and fallback:
 * <ul>
 *   <li>clean verdict: published (201 on create, 200 on update);</li>
 *   <li>isHateful: 403 COMMENT_POST_NOT_ALLOWED;</li>
 *   <li>404 / 500 / dropped connection: 503 MODERATION_UNAVAILABLE after every retry attempt,
 *       nothing published (fail-closed);</li>
 *   <li>a rating-only review never calls the service.</li>
 * </ul>
 * A read timeout takes the same path (exception → fallback) and is covered by
 * {@code CommentModerationClientTest}, to keep this IT clear of the 10 s client timeout.
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ReviewModerationFlowIT — comment moderation verdicts and unavailability")
class ReviewModerationFlowIT {

    private static final String ANALYZE_PATH = "/api/v1/moderation/analyze";
    private static final String CREATE_ENDPOINT = "/api/v1/services/review/create";
    private static final String UPDATE_ENDPOINT = "/api/v1/services/review/update/{reviewUuid}";
    private static final String MODERATION_CIRCUIT_BREAKER = "moderation";
    private static final String COMMENT = "Très bon produit, livré vite.";

    /** How the simulated moderation service answers. */
    enum Moderation { CLEAN, HATEFUL, NOT_FOUND, SERVER_ERROR, DROPPED }

    /** Failures that must read as "moderation unavailable", never as a rejected comment. */
    enum Outage { NOT_FOUND, SERVER_ERROR, DROPPED }

    private static final AtomicReference<Moderation> MODERATION = new AtomicReference<>(Moderation.CLEAN);
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final HttpServer MODERATION_SERVER = startModerationServer();

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

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${resilience4j.retry.instances.moderation.max-attempts}")
    private int moderationMaxAttempts;

    private String keycloakId;
    private UUID productUuid;
    private UUID orderUuid;

    private static HttpServer startModerationServer() {
        try {
            final HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext(ANALYZE_PATH, ReviewModerationFlowIT::answer);
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void answer(final HttpExchange exchange) throws IOException {
        CALLS.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        switch (MODERATION.get()) {
            case CLEAN -> respond(exchange, 200, "{\"label\":\"non-toxic\",\"score\":0.98,\"isHateful\":false}");
            case HATEFUL -> respond(exchange, 200, "{\"label\":\"toxic\",\"score\":0.99,\"isHateful\":true}");
            case NOT_FOUND -> respond(exchange, 404, "{\"error\":\"Not Found\"}");
            case SERVER_ERROR -> respond(exchange, 500, "{\"error\":\"model crashed\"}");
            case DROPPED -> exchange.close();
        }
    }

    private static void respond(final HttpExchange exchange, final int status, final String json) throws IOException {
        final byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @DynamicPropertySource
    static void moderationUrl(final DynamicPropertyRegistry registry) {
        registry.add("moderation.api.url",
                () -> "http://localhost:" + MODERATION_SERVER.getAddress().getPort() + ANALYZE_PATH);
    }

    @AfterAll
    static void stopModerationServer() {
        MODERATION_SERVER.stop(0);
    }

    @BeforeEach
    void seed() {
        testDataCleaner.wipe();
        MODERATION.set(Moderation.CLEAN);
        CALLS.set(0);
        // Outage tests record failures: start each test with a closed circuit.
        circuitBreakerRegistry.circuitBreaker(MODERATION_CIRCUIT_BREAKER).reset();
        keycloakId = "kc-moderated-" + UUID.randomUUID();
        transactionTemplate.executeWithoutResult(tx -> {
            final UserEntity user = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                    .keycloakId(keycloakId).email("moderated+" + UUID.randomUUID() + "@example.com").build());
            final ProductEntity product = productRepository.save(ProductEntityBuilder.aValidProductBuilder()
                    .name("Clavier " + UUID.randomUUID()).category("KEYBOARD").build());
            productUuid = product.getUuid();

            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user)
                    .status(OrderStatus.DELIVERED)
                    .orderItemEntities(new ArrayList<>())
                    .build();
            final OrderItemEntity item = OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .productEntity(product)
                    .orderEntity(order)
                    .build();
            order.getOrderItemEntities().add(item);
            orderUuid = orderRepository.save(order).getUuid();
        });
    }

    private ResultActions create(final String comment) throws Exception {
        return mockMvc.perform(post(CREATE_ENDPOINT)
                .with(jwtUser(keycloakId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(ReviewCreateRequestDto.builder()
                        .orderUuid(orderUuid)
                        .productUuid(productUuid)
                        .rating(4)
                        .comment(comment)
                        .build())));
    }

    private ResultActions update(final UUID reviewUuid, final String comment) throws Exception {
        return mockMvc.perform(patch(UPDATE_ENDPOINT, reviewUuid)
                .with(jwtUser(keycloakId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(ReviewUpdateRequestDto.builder()
                        .reviewUuid(reviewUuid)
                        .comment(comment)
                        .build())));
    }

    /** A published rating-only review (no moderation call), then the call counter is reset. */
    private UUID existingRatingOnlyReview() throws Exception {
        final String body = create(null).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        CALLS.set(0);
        return UUID.fromString(objectMapper.readTree(body).get("uuid").asString());
    }

    private String storedComment(final UUID reviewUuid) {
        return reviewRepository.findByUuid(reviewUuid).orElseThrow().getComment();
    }

    @Test
    @DisplayName("create: clean verdict -> 201, comment published, one call on the analyze path")
    void createClean() throws Exception {
        create(COMMENT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value(COMMENT));

        assertThat(CALLS.get()).isEqualTo(1);
        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("create: isHateful -> 403 COMMENT_POST_NOT_ALLOWED, nothing saved")
    void createHateful() throws Exception {
        MODERATION.set(Moderation.HATEFUL);

        create(COMMENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMENT_POST_NOT_ALLOWED"));

        assertThat(reviewRepository.count()).isZero();
    }

    @ParameterizedTest(name = "create: {0} -> 503 MODERATION_UNAVAILABLE after the retries, nothing saved")
    @EnumSource(Outage.class)
    void createUnavailable(final Outage outage) throws Exception {
        MODERATION.set(Moderation.valueOf(outage.name()));

        create(COMMENT)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("MODERATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));

        assertThat(CALLS.get()).isEqualTo(moderationMaxAttempts);
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    @DisplayName("create: a rating-only review is published without calling the service")
    void createRatingOnlySkipsModeration() throws Exception {
        MODERATION.set(Moderation.NOT_FOUND);

        create(null).andExpect(status().isCreated());

        assertThat(CALLS.get()).isZero();
    }

    @Test
    @DisplayName("update: clean verdict -> 200, comment published")
    void updateClean() throws Exception {
        final UUID reviewUuid = existingRatingOnlyReview();

        update(reviewUuid, COMMENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value(COMMENT));

        assertThat(CALLS.get()).isEqualTo(1);
        assertThat(storedComment(reviewUuid)).isEqualTo(COMMENT);
    }

    @Test
    @DisplayName("update: isHateful -> 403 COMMENT_POST_NOT_ALLOWED, stored review unchanged")
    void updateHateful() throws Exception {
        final UUID reviewUuid = existingRatingOnlyReview();
        MODERATION.set(Moderation.HATEFUL);

        update(reviewUuid, COMMENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMENT_POST_NOT_ALLOWED"));

        assertThat(storedComment(reviewUuid)).isNull();
    }

    @ParameterizedTest(name = "update: {0} -> 503 MODERATION_UNAVAILABLE, stored review unchanged")
    @EnumSource(Outage.class)
    void updateUnavailable(final Outage outage) throws Exception {
        final UUID reviewUuid = existingRatingOnlyReview();
        MODERATION.set(Moderation.valueOf(outage.name()));

        update(reviewUuid, COMMENT)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("MODERATION_UNAVAILABLE"));

        assertThat(CALLS.get()).isEqualTo(moderationMaxAttempts);
        assertThat(storedComment(reviewUuid)).isNull();
    }
}
