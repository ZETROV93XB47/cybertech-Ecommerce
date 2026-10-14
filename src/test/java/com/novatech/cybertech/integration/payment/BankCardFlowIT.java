package com.novatech.cybertech.integration.payment;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.dto.data.PaymentAttemptResult;
import com.novatech.cybertech.dto.data.PaymentSource;
import com.novatech.cybertech.dto.data.StripeCardDetailsDto;
import com.novatech.cybertech.dto.data.StripeSetupIntentDto;
import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemAddRequestDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.BankCardType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.OrderDtoFixtures;
import com.novatech.cybertech.fixtures.support.stubs.StripeEventBuilder;
import com.novatech.cybertech.repositories.BankCardRepository;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import com.novatech.cybertech.services.core.StripeCardGateway;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saved card at Stripe, end to end on the real stack (MySQL, Redis, ES, Mongo).
 *
 * <p>Stripe is replaced at its two seams: {@link StripeCardGateway} (Customer, SetupIntent,
 * PaymentMethod) is a mock, and {@link PaymentAttemptProcessor} is a mock that behaves like Stripe's
 * test cards: it declines the PaymentMethods prefixed {@value #DECLINING_PM_PREFIX} (as
 * 4000 0000 0000 0341 does once saved) and accepts any other one. PaymentMethod ids are unique per
 * test (the database is shared across tests and the column is unique, as at Stripe). Payment outcomes then reach the order through signed
 * {@code payment_intent.*} webhooks, as in production.</p>
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Own context (gateway mock + Stripe keys): closed after the class so its containers (Elasticsearch
// included) do not stay alive for the rest of the IT suite and starve the later contexts.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import({TestcontainersConfiguration.class, BankCardFlowIT.TestPaymentConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "stripe.publishable-key=" + BankCardFlowIT.PUBLISHABLE_KEY,
        "stripe.webhook.secret=" + BankCardFlowIT.WEBHOOK_SECRET
})
class BankCardFlowIT {

    static final String PUBLISHABLE_KEY = "pk_test_bank_card_flow_it";
    static final String WEBHOOK_SECRET = "whsec_bank_card_flow_it_secret";

    private static final String BANK_CARD = "/api/v1/services/bank-card";
    private static final String PLACE = "/api/v1/services/management/order/place";
    private static final String RETRY = "/api/v1/services/management/order/retry-payment/";
    private static final String WEBHOOK = "/api/v1/webhooks/stripe";
    /** Prefix of the PaymentMethods the mocked processor declines, like Stripe's 4000 0000 0000 0341. */
    private static final String DECLINING_PM_PREFIX = "pm_declining_";
    private static final int FUTURE_YEAR = YearMonth.now().getYear() + 4;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired BankCardRepository bankCardRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired PaymentAttemptProcessor stripePaymentProcessor;

    @MockitoBean StripeCardGateway stripeCardGateway;

    private String keycloakId;
    private String customerId;
    private UUID productUuid;
    private String acceptingPm;
    private String secondAcceptingPm;
    private String decliningPm;

    @BeforeEach
    void seed() {
        keycloakId = "kc-card-" + UUID.randomUUID();
        customerId = "cus_" + UUID.randomUUID().toString().replace("-", "");
        final String suffix = UUID.randomUUID().toString().replace("-", "");
        acceptingPm = "pm_visa_" + suffix;
        secondAcceptingPm = "pm_mastercard_" + suffix;
        decliningPm = DECLINING_PM_PREFIX + suffix;
        final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                .email("card-" + UUID.randomUUID() + "@example.com")
                .keycloakId(keycloakId)
                .stripeCustomerId(null)
                .build();
        userRepository.save(user);
        final ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                .name("Card flow product " + UUID.randomUUID())
                .stock(10)
                .reservedStock(0)
                .build();
        productRepository.save(product);
        productUuid = product.getUuid();

        when(stripeCardGateway.createCustomer(any(), anyString(), anyString())).thenReturn(customerId);
        when(stripeCardGateway.createSetupIntent(anyString())).thenAnswer(inv -> new StripeSetupIntentDto(
                "seti_" + UUID.randomUUID().toString().replace("-", ""), "seti_secret", inv.getArgument(0),
                "requires_payment_method", null));

        Mockito.reset(stripePaymentProcessor);
        when(stripePaymentProcessor.processPayment(any(UUID.class), any(), anyString(), any())).thenAnswer(inv -> {
            final PaymentSource source = inv.getArgument(3);
            final PaymentAttemptStatus outcome = source.paymentMethodId().startsWith(DECLINING_PM_PREFIX)
                    ? PaymentAttemptStatus.FAILED
                    : PaymentAttemptStatus.SUCCESS;
            return new PaymentAttemptResult(outcome, "pi_" + UUID.randomUUID().toString().replace("-", ""));
        });
    }

    @Test
    void setupIntentCreatesTheStripeCustomerOnceAndReturnsWhatElementsNeeds() throws Exception {
        mockMvc.perform(post(BANK_CARD + "/setup-intent").with(jwtUser(keycloakId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientSecret").value("seti_secret"))
                .andExpect(jsonPath("$.publishableKey").value(PUBLISHABLE_KEY))
                .andExpect(jsonPath("$.setupIntentId").isNotEmpty());
        mockMvc.perform(post(BANK_CARD + "/setup-intent").with(jwtUser(keycloakId)))
                .andExpect(status().isCreated());

        verify(stripeCardGateway, Mockito.times(1)).createCustomer(any(), anyString(), anyString());
        assertThat(userRepository.findByKeycloakId(keycloakId).orElseThrow().getStripeCustomerId()).isEqualTo(customerId);
    }

    @Test
    void savesTheConfirmedCardThenReplacesUpdatesAndDeletesIt() throws Exception {
        saveCard("visa", acceptingPm, "4242");

        final MvcResult get = mockMvc.perform(get(BANK_CARD + "/get").with(jwtUser(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedNumber").value("**** **** **** 4242"))
                .andExpect(jsonPath("$.cardType").value("VISA"))
                .andExpect(jsonPath("$.expiryDate").value("12/" + FUTURE_YEAR))
                .andReturn();
        // Neither the PaymentMethod id nor any card number beyond the last four digits leaks.
        assertThat(get.getResponse().getContentAsString()).doesNotContain(acceptingPm).doesNotContain("424242");
        verify(stripeCardGateway).setDefaultPaymentMethod(customerId, acceptingPm);

        // A second card replaces the first one (one card per user); the old one is detached after commit.
        saveCard("mastercard", secondAcceptingPm, "4444");
        final List<BankCardEntity> cards = bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId);
        assertThat(cards).singleElement().satisfies(card -> {
            assertThat(card.getStripePaymentMethodId()).isEqualTo(secondAcceptingPm);
            assertThat(card.getCardType()).isEqualTo(BankCardType.MASTERCARD);
            assertThat(card.getLastFourDigits()).isEqualTo("4444");
        });
        verify(stripeCardGateway).detachPaymentMethod(acceptingPm);

        mockMvc.perform(put(BANK_CARD + "/update").with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardHolderName\":\"Jane Q Doe\",\"expiryDate\":\"03/" + FUTURE_YEAR + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardHolderName").value("Jane Q Doe"))
                .andExpect(jsonPath("$.expiryDate").value("03/" + FUTURE_YEAR));
        verify(stripeCardGateway).updatePaymentMethod(secondAcceptingPm, "Jane Q Doe", 3, FUTURE_YEAR);

        mockMvc.perform(delete(BANK_CARD + "/delete").with(jwtUser(keycloakId)))
                .andExpect(status().isNoContent());
        assertThat(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).isEmpty();
        verify(stripeCardGateway).detachPaymentMethod(secondAcceptingPm);
    }

    @Test
    void setupIntentOfAnotherCustomerIsRejectedWith403() throws Exception {
        final String setupIntentId = createSetupIntent();
        when(stripeCardGateway.retrieveSetupIntent(setupIntentId)).thenReturn(
                new StripeSetupIntentDto(setupIntentId, "s", "cus_someone_else", "succeeded", "pm_foreign"));

        mockMvc.perform(post(BANK_CARD + "/add").with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setupIntentId\":\"" + setupIntentId + "\"}"))
                .andExpect(status().isForbidden());

        assertThat(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).isEmpty();
        verify(stripeCardGateway, never()).retrievePaymentMethod(anyString());
    }

    @Test
    void theFormerBodyWithACardNumberIsRejected() throws Exception {
        mockMvc.perform(post(BANK_CARD + "/add").with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardHolderName\":\"Jane\",\"cardNumber\":\"4242424242424242\","
                                + "\"expiryDate\":\"12/2030\",\"cardType\":\"VISA\"}"))
                .andExpect(status().isBadRequest());
        assertThat(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).isEmpty();
    }

    @Test
    void placingAnOrderWithoutACardIs404BankCardNotFound() throws Exception {
        addItemToCart();

        mockMvc.perform(post(PLACE).with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(placeRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BANK_CARD_NOT_FOUND"));
        verify(stripePaymentProcessor, never()).processPayment(any(), any(), anyString(), any());
    }

    @Test
    void savedCardIsChargedAndPaysTheOrder() throws Exception {
        saveCard("visa", acceptingPm, "4242");
        addItemToCart();

        final UUID orderUuid = placeOrder();

        final ArgumentCaptor<PaymentSource> source = ArgumentCaptor.forClass(PaymentSource.class);
        verify(stripePaymentProcessor).processPayment(any(UUID.class), any(), anyString(), source.capture());
        assertThat(source.getValue()).isEqualTo(new PaymentSource(customerId, acceptingPm));

        sendWebhook("payment_intent.succeeded", "succeeded", lastAttempt(orderUuid), orderUuid);
        awaitOrderStatus(orderUuid, OrderStatus.PAID);
    }

    @Test
    void declinedCardFailsThenRetryWithANewCardPays() throws Exception {
        saveCard("visa", decliningPm, "0341");
        addItemToCart();

        final UUID orderUuid = placeOrder();
        final PaymentEntity failedAttempt = lastAttempt(orderUuid);
        assertThat(failedAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        sendWebhook("payment_intent.payment_failed", "requires_payment_method", failedAttempt, orderUuid);
        awaitOrderStatus(orderUuid, OrderStatus.PAYMENT_FAILED);

        saveCard("visa", acceptingPm, "4242");
        mockMvc.perform(post(RETRY + orderUuid).with(jwtUser(keycloakId)))
                .andExpect(status().isOk());

        final PaymentEntity retryAttempt = lastAttempt(orderUuid);
        assertThat(retryAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        final ArgumentCaptor<PaymentSource> sources = ArgumentCaptor.forClass(PaymentSource.class);
        verify(stripePaymentProcessor, Mockito.times(2)).processPayment(any(UUID.class), any(), anyString(), sources.capture());
        assertThat(sources.getAllValues()).extracting(PaymentSource::paymentMethodId).containsExactly(decliningPm, acceptingPm);

        sendWebhook("payment_intent.succeeded", "succeeded", retryAttempt, orderUuid);
        awaitOrderStatus(orderUuid, OrderStatus.PAID);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String createSetupIntent() throws Exception {
        final MvcResult result = mockMvc.perform(post(BANK_CARD + "/setup-intent").with(jwtUser(keycloakId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("setupIntentId").asString();
    }

    /** Runs the two-step flow the frontend runs: setup-intent, (Elements confirms), then add. */
    private void saveCard(final String brand, final String paymentMethodId, final String last4) throws Exception {
        final String setupIntentId = createSetupIntent();
        when(stripeCardGateway.retrieveSetupIntent(setupIntentId)).thenReturn(
                new StripeSetupIntentDto(setupIntentId, "s", customerId, "succeeded", paymentMethodId));
        when(stripeCardGateway.retrievePaymentMethod(paymentMethodId)).thenReturn(
                new StripeCardDetailsDto(paymentMethodId, customerId, brand, last4, 12, FUTURE_YEAR, "Jane Doe"));

        mockMvc.perform(post(BANK_CARD + "/add").with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setupIntentId\":\"" + setupIntentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedNumber").value("**** **** **** " + last4));
    }

    private void addItemToCart() throws Exception {
        final CartCreateRequestDto body = CartCreateRequestDto.builder()
                .cartItemAddRequestDtos(List.of(CartItemAddRequestDto.builder().productUuid(productUuid).quantity(1).build()))
                .build();
        mockMvc.perform(post("/api/v1/services/cart/add").with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isLessThan(300));
    }

    private static OrderPlacingRequestDto placeRequest() {
        return OrderDtoFixtures.aValidPlaceOrderRequestBuilder().build();
    }

    private UUID placeOrder() throws Exception {
        final MvcResult result = mockMvc.perform(post(PLACE).with(jwtUser(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(placeRequest())))
                .andExpect(status().isCreated())
                .andReturn();
        final JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("uuid").asString());
    }

    private PaymentEntity lastAttempt(final UUID orderUuid) {
        return transactionTemplate.execute(tx -> {
            final OrderEntity order = orderRepository.findByUuid(orderUuid).orElseThrow();
            return order.getPaymentAttempts().stream()
                    .max(Comparator.comparing(PaymentEntity::getCreatedAt).thenComparing(PaymentEntity::getId))
                    .orElseThrow();
        });
    }

    private void awaitOrderStatus(final UUID orderUuid, final OrderStatus expected) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orderRepository.findByUuid(orderUuid).orElseThrow().getStatus()).isEqualTo(expected));
    }

    private void sendWebhook(final String type, final String intentStatus, final PaymentEntity attempt,
                             final UUID orderUuid) throws Exception {
        final long amountInCents = attempt.getAmount().getAmount().movePointRight(2).longValueExact();
        final String payload = """
                {
                  "id": "evt_%s",
                  "object": "event",
                  "api_version": "2024-04-10",
                  "created": %d,
                  "type": "%s",
                  "livemode": false,
                  "pending_webhooks": 0,
                  "request": {"id": null, "idempotency_key": null},
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "amount": %d,
                      "amount_received": %d,
                      "currency": "eur",
                      "status": "%s",
                      "metadata": {"order_uuid": "%s", "idempotency_key": "%s"}
                    }
                  }
                }
                """.formatted(UUID.randomUUID().toString().replace("-", ""), System.currentTimeMillis() / 1000L,
                type, attempt.getStripePaymentID(), amountInCents, amountInCents, intentStatus, orderUuid,
                attempt.getIdempotencyKey());
        final StripeEventBuilder.Signed signed = StripeEventBuilder.signedPayloadNow(WEBHOOK_SECRET, payload);
        mockMvc.perform(post(WEBHOOK)
                        .header("Stripe-Signature", signed.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signed.payload()))
                .andExpect(status().isOk());
    }

    /** Same Stripe-processor override as {@code OrderFlowIT}: VISA/MASTERCARD routed to the mock. */
    @TestConfiguration
    static class TestPaymentConfig {

        @Bean
        @Primary
        PaymentAttemptProcessor stripePaymentProcessorMock() {
            return Mockito.mock(PaymentAttemptProcessor.class);
        }

        @Bean
        @Primary
        Map<Set<PaymentType>, PaymentAttemptProcessor> testPaymentServiceMap(final PaymentAttemptProcessor stripePaymentProcessorMock) {
            final Map<Set<PaymentType>, PaymentAttemptProcessor> map = new HashMap<>();
            map.put(Set.of(PaymentType.VISA, PaymentType.MASTERCARD), stripePaymentProcessorMock);
            return map;
        }
    }
}
