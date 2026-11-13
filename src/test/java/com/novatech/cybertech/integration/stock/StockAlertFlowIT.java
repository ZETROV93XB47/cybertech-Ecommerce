package com.novatech.cybertech.integration.stock;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.batch.task.StockAlertTasklet;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.StockAlertEntity;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.StockAlertStatus;
import com.novatech.cybertech.exceptions.StockAlertAlreadyExistsException;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.fixtures.support.stubs.ModerationStub;
import com.novatech.cybertech.repositories.NotificationRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.StockAlertRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.StockAlertService;
import com.novatech.cybertech.services.core.StockService;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Back-in-stock alert end to end, on real MySQL / Redis: a user subscribes through the API to a
 * product at 0, the stock comes back (admin update, or units released by an order cancellation),
 * {@link StockAlertTasklet} runs and the e-mail goes out exactly once. SMTP is the only stand-in:
 * {@link JavaMailSender#send(MimeMessage)} is stubbed on a spy, the real MIME message is captured.
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ModerationStub.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("StockAlertFlowIT — subscribe, restock, job, one e-mail")
class StockAlertFlowIT {

    private static final String STOCK_ALERT_ENDPOINT = "/api/v1/services/stock-alerts/{productUuid}";
    private static final String PRODUCT_UPDATE_ENDPOINT = "/api/v1/services/admin/management/product/update/{productUuid}";
    private static final String PRODUCT_DELETE_ENDPOINT = "/api/v1/services/admin/management/product/delete/{productUuid}";
    private static final String ADMIN_KC = "kc-admin-stock-alert";
    private static final String ERROR_CODE = "$.errorCode";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockAlertRepository stockAlertRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private StockAlertService stockAlertService;
    @Autowired private StockService stockService;
    @Autowired private StockAlertTasklet stockAlertTasklet;
    @Autowired private TestDataCleaner testDataCleaner;

    @MockitoSpyBean
    private JavaMailSender mailSender;

    private String keycloakId;
    private String email;
    private ProductEntity product;

    @BeforeEach
    void seed() {
        testDataCleaner.wipe();
        doNothing().when(mailSender).send(any(MimeMessage.class));
        keycloakId = "kc-stock-alert-" + UUID.randomUUID();
        email = "alert+" + UUID.randomUUID() + "@example.com";
        userRepository.save(UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).email(email).build());
        product = productRepository.save(ProductEntityBuilder.aValidProductBuilder()
                .name("Écran " + UUID.randomUUID()).stock(0).reservedStock(0).build());
    }

    @Test
    @DisplayName("product at 0 -> subscribe -> admin restocks -> job -> one e-mail, alert NOTIFIED; second run sends nothing")
    void adminRestockSendsOneEmail() throws Exception {
        subscribe(keycloakId);

        // Still out of stock: the job finds nothing to send.
        runJob();
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(alert().getStatus()).isEqualTo(StockAlertStatus.PENDING);

        restockAsAdmin(5);
        runJob();

        final MimeMessage sent = capturedEmail();
        assertThat(sent.getSubject()).isEqualTo(EmailTemplateType.STOCK_ALERT.getNotificationSubject().getSubject());
        assertThat(sent.getRecipients(Message.RecipientType.TO)).extracting(Object::toString).containsExactly(email);
        final StockAlertEntity notified = alert();
        assertThat(notified.getStatus()).isEqualTo(StockAlertStatus.NOTIFIED);
        assertThat(notified.getNotifiedAt()).isNotNull();
        assertThat(stockAlertNotifications()).hasSize(1);

        // One alert = one send: the next run of the job sends nothing.
        clearInvocations(mailSender);
        runJob();
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(stockAlertNotifications()).hasSize(1);
    }

    @Test
    @DisplayName("units released by an order cancellation also trigger the alert")
    void stockReleasedByCancellationTriggersTheAlert() throws Exception {
        // 2 in stock, both reserved by an unpaid order: available = 0.
        setStock(2);
        final UUID orderUuid = UUID.randomUUID();
        stockService.reserveStock(orderUuid, Map.of(product.getUuid(), 2));
        subscribe(keycloakId);

        // The order is cancelled: its reservation goes back to the stock.
        stockService.releaseStock(orderUuid);
        runJob();

        capturedEmail();
        assertThat(alert().getStatus()).isEqualTo(StockAlertStatus.NOTIFIED);
    }

    @Test
    @DisplayName("subscribing twice at the same time: one alert row, the other call gets STOCK_ALERT_ALREADY_EXISTS")
    void concurrentSubscriptionsKeepOneRow() throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final Callable<Throwable> subscribeCall = () -> {
            start.await();
            try {
                stockAlertService.subscribe(keycloakId, product.getUuid());
                return null;
            } catch (Exception e) {
                return e;
            }
        };
        final List<Throwable> outcomes;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            final Future<Throwable> first = pool.submit(subscribeCall);
            final Future<Throwable> second = pool.submit(subscribeCall);
            start.countDown();
            outcomes = java.util.Arrays.asList(first.get(), second.get());
        }

        assertThat(outcomes).filteredOn(java.util.Objects::isNull).hasSize(1);
        assertThat(outcomes).filteredOn(java.util.Objects::nonNull)
                .singleElement().isInstanceOf(StockAlertAlreadyExistsException.class);
        assertThat(stockAlertRepository.count()).isEqualTo(1);

        // Through the API the second call is a 409.
        mockMvc.perform(post(STOCK_ALERT_ENDPOINT, product.getUuid()).with(jwtUser(keycloakId)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(ERROR_CODE).value("STOCK_ALERT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("product already available: 409 PRODUCT_ALREADY_IN_STOCK, no alert row")
    void productInStockIsRefused() throws Exception {
        setStock(3);

        mockMvc.perform(post(STOCK_ALERT_ENDPOINT, product.getUuid()).with(jwtUser(keycloakId)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(ERROR_CODE).value("PRODUCT_ALREADY_IN_STOCK"));
        assertThat(stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(keycloakId, product.getUuid())).isEmpty();
    }

    @Test
    @DisplayName("unsubscribed alert is not sent when the stock comes back")
    void cancelledAlertIsNotSent() throws Exception {
        subscribe(keycloakId);
        mockMvc.perform(delete(STOCK_ALERT_ENDPOINT, product.getUuid()).with(jwtUser(keycloakId)).with(csrf()))
                .andExpect(status().isNoContent());

        setStock(4);
        runJob();

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(alert().getStatus()).isEqualTo(StockAlertStatus.CANCELLED);
    }

    @Test
    @DisplayName("deleting a product that has alerts works and removes its alerts")
    void deletingProductWithAlerts() throws Exception {
        subscribe(keycloakId);

        mockMvc.perform(delete(PRODUCT_DELETE_ENDPOINT, product.getUuid()).with(jwtAdmin(ADMIN_KC)).with(csrf()))
                .andExpect(status().is2xxSuccessful());

        assertThat(productRepository.findByUuid(product.getUuid())).isEmpty();
        assertThat(stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(keycloakId, product.getUuid())).isEmpty();
    }

    // ---------------------------------------------------------------------------------

    private void subscribe(final String userKeycloakId) throws Exception {
        mockMvc.perform(post(STOCK_ALERT_ENDPOINT, product.getUuid()).with(jwtUser(userKeycloakId)).with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private void restockAsAdmin(final int stock) throws Exception {
        final ProductEntity current = productRepository.findByUuid(product.getUuid()).orElseThrow();
        final ProductUpdateRequestDto patch = new ProductUpdateRequestDto();
        patch.setProductUuid(current.getUuid());
        patch.setBrand(current.getBrand());
        patch.setDescription(current.getDescription());
        patch.setStock(stock);
        mockMvc.perform(patch(PRODUCT_UPDATE_ENDPOINT, current.getUuid()).with(jwtAdmin(ADMIN_KC)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk());
        assertThat(productRepository.findByUuid(product.getUuid()).orElseThrow().getStock()).isEqualTo(stock);
    }

    private void setStock(final int stock) {
        final ProductEntity current = productRepository.findByUuid(product.getUuid()).orElseThrow();
        current.setStock(stock);
        productRepository.save(current);
    }

    private void runJob() throws Exception {
        stockAlertTasklet.execute(mock(StepContribution.class), (BaseTasklet.StepArguments) null);
    }

    private StockAlertEntity alert() {
        return stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(keycloakId, product.getUuid()).orElseThrow();
    }

    private MimeMessage capturedEmail() {
        final ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private List<NotificationEntity> stockAlertNotifications() {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getNotificationType() == NotificationType.STOCK_ALERT)
                .toList();
    }
}
