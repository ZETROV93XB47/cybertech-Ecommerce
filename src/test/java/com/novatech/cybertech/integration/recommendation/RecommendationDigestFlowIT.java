package com.novatech.cybertech.integration.recommendation;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.batch.task.RecommendationDigestTasklet;
import com.novatech.cybertech.clients.GorseClient;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.RecommendationEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.fixtures.support.stubs.ModerationStub;
import com.novatech.cybertech.repositories.NotificationRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.RecommendationRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.RecommendationDigestService;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Daily product-suggestion e-mail end to end, on real MySQL: {@link RecommendationDigestTasklet}
 * reads the recipients, {@code RecommendationDigestServiceImp} asks a fake Gorse
 * ({@link MockRestServiceServer}) then the best sellers, records the products sent and the
 * e-mail goes out; the unsubscribe link of the e-mail turns the e-mails off. Stand-ins: Gorse and
 * {@link JavaMailSender#send(MimeMessage)} (stubbed on a spy, the real MIME message is captured).
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ModerationStub.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("RecommendationDigestFlowIT — fake Gorse, one e-mail per day, unsubscribe link")
class RecommendationDigestFlowIT {

    private static final String GORSE_URL = "http://gorse.test";
    /** 6 products per e-mail, 3 candidates asked per product. */
    private static final int CANDIDATES_ASKED = 18;
    private static final String UNSUBSCRIBE_ENDPOINT = "/api/v1/services/recommendations/unsubscribe/{token}";
    private static final String GORSE_CLIENT_FIELD = "gorseClient";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private RecommendationRepository recommendationRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private RecommendationDigestService recommendationDigestService;
    @Autowired private RecommendationDigestTasklet tasklet;
    @Autowired private GorseClient realGorseClient;
    @Autowired private TestDataCleaner testDataCleaner;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private JavaMailSender mailSender;

    private MockRestServiceServer gorse;
    private UserEntity subscriber;
    private UserEntity optedOut;
    private ProductEntity gorseFirst;
    private ProductEntity gorseSecond;
    private ProductEntity gorseOutOfStock;
    private ProductEntity bestSeller;

    @BeforeEach
    void seed() {
        testDataCleaner.wipe();
        doNothing().when(mailSender).send(any(MimeMessage.class));

        subscriber = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .keycloakId("kc-digest-" + UUID.randomUUID()).email("digest+" + UUID.randomUUID() + "@example.com").build());
        optedOut = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .keycloakId("kc-digest-off-" + UUID.randomUUID()).email("digest-off+" + UUID.randomUUID() + "@example.com")
                .recommendationEmailEnabled(false).build());

        gorseFirst = saveProduct("Laptop", 5);
        gorseSecond = saveProduct("Keyboard", 5);
        gorseOutOfStock = saveProduct("Monitor", 0);
        bestSeller = saveProduct("Phone", 5);

        // Fake Gorse plugged into the real service bean (restored after each test).
        final RestClient.Builder builder = RestClient.builder();
        gorse = MockRestServiceServer.bindTo(builder).build();
        final GorseClient fakeGorseClient = new GorseClient(builder.build());
        ReflectionTestUtils.setField(fakeGorseClient, "gorseApiUrl", GORSE_URL);
        ReflectionTestUtils.setField((Object) AopTestUtils.getUltimateTargetObject(recommendationDigestService), GORSE_CLIENT_FIELD, fakeGorseClient);
    }

    @AfterEach
    void restoreGorseClient() {
        ReflectionTestUtils.setField((Object) AopTestUtils.getUltimateTargetObject(recommendationDigestService), GORSE_CLIENT_FIELD, realGorseClient);
    }

    @Test
    @DisplayName("Gorse picks first, out-of-stock skipped, best sellers fill; only the subscribed user is e-mailed; rerun the same day sends nothing")
    void sendsOneEmailToSubscribedUsersOnly() throws Exception {
        gorse.expect(ExpectedCount.once(),
                        requestTo(GORSE_URL + "/api/recommend/" + subscriber.getKeycloakId() + "?n=" + CANDIDATES_ASKED))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[\"%s\",\"%s\",\"%s\"]".formatted(gorseFirst.getUuid(), gorseOutOfStock.getUuid(), gorseSecond.getUuid()),
                        MediaType.APPLICATION_JSON));

        runJob();

        gorse.verify();
        final MimeMessage sent = capturedEmail();
        assertThat(sent.getSubject()).isEqualTo(EmailTemplateType.RECOMMENDATION_DIGEST.getNotificationSubject().getSubject());
        assertThat(sent.getRecipients(Message.RecipientType.TO)).extracting(Object::toString).containsExactly(subscriber.getEmail());

        // Products recorded as sent, in e-mail order (score = rank): Gorse's, then the best seller.
        assertThat(sentRows(subscriber))
                .extracting(r -> r.getProduct().getUuid(), RecommendationEntity::getScore)
                .containsExactly(
                        tuple(gorseFirst.getUuid(), 3.0),
                        tuple(gorseSecond.getUuid(), 2.0),
                        tuple(bestSeller.getUuid(), 1.0));
        assertThat(sentRows(optedOut)).isEmpty();
        assertThat(userRepository.findById(subscriber.getId()).orElseThrow().getRecommendationEmailUnsubscribeToken()).isNotNull();
        assertThat(digestNotifications()).hasSize(1);

        // Same day: the 20 h guard stops the second run before Gorse and the e-mail.
        clearInvocations(mailSender);
        runJob();
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(sentRows(subscriber)).hasSize(3);
        assertThat(digestNotifications()).hasSize(1);
    }

    @Test
    @DisplayName("Gorse has nothing for a new user: the e-mail is made of best sellers")
    void coldStartUserGetsBestSellers() throws Exception {
        gorse.expect(ExpectedCount.once(),
                        requestTo(GORSE_URL + "/api/recommend/" + subscriber.getKeycloakId() + "?n=" + CANDIDATES_ASKED))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        runJob();

        capturedEmail();
        assertThat(sentRows(subscriber))
                .extracting(r -> r.getProduct().getUuid())
                .containsExactlyInAnyOrder(gorseFirst.getUuid(), gorseSecond.getUuid(), bestSeller.getUuid());
    }

    @Test
    @DisplayName("the unsubscribe link (no login) turns the e-mails off; an unknown token is a 404")
    void unsubscribeLinkTurnsEmailsOff() throws Exception {
        final UUID token = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(tx -> {
            final UserEntity user = userRepository.findById(subscriber.getId()).orElseThrow();
            user.setRecommendationEmailUnsubscribeToken(token);
            userRepository.save(user);
        });

        mockMvc.perform(post(UNSUBSCRIBE_ENDPOINT, token).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(subscriber.getId()).orElseThrow().isRecommendationEmailEnabled()).isFalse();

        mockMvc.perform(post(UNSUBSCRIBE_ENDPOINT, UUID.randomUUID()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").exists());

        // Nobody left to e-mail: the job neither calls Gorse nor sends anything.
        runJob();
        gorse.verify();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ---------------------------------------------------------------------------------

    private ProductEntity saveProduct(final String name, final int stock) {
        return productRepository.save(ProductEntityBuilder.aValidProductBuilder()
                .name(name + " " + UUID.randomUUID()).stock(stock).reservedStock(0).build());
    }

    private void runJob() throws Exception {
        tasklet.execute(mock(StepContribution.class), (BaseTasklet.StepArguments) null);
    }

    private MimeMessage capturedEmail() {
        final ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private List<RecommendationEntity> sentRows(final UserEntity user) {
        return transactionTemplate.execute(tx -> recommendationRepository.findAll().stream()
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .sorted(Comparator.comparing(RecommendationEntity::getScore).reversed())
                .peek(r -> r.getProduct().getUuid())
                .toList());
    }

    private List<NotificationEntity> digestNotifications() {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getNotificationType() == NotificationType.RECOMMENDATION_DIGEST)
                .toList();
    }
}
