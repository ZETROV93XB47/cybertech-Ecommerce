package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.NotificationRedrivePayload;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.repositories.NotificationRepository;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedeliverFailedNotificationsTasklet}.
 *
 * <p>Mirrors {@link CleanUpExpiredStockReservationsTaskletTest} (same
 * fixture-building style, same {@link BaseTasklet.StepArguments} setup).
 * Covers the four cases called out in the Phase 3 spec:
 * <ol>
 *   <li>No candidates returned → no-op, no recorder/retryable interactions.</li>
 *   <li>Single redrivable candidate → retryable bean invoked once and the
 *       coordination row's count is bumped, status stays PENDING_RETRY.</li>
 *   <li>Cumulative cap reached after the bump → row flips to FAILED.</li>
 *   <li>Corrupted/null payload → row goes straight to FAILED, retryable bean
 *       NEVER invoked.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RedeliverFailedNotificationsTaskletTest {

    private static final int CUMULATIVE_MAX_ATTEMPTS = 9;
    private static final long BACKOFF_MINUTES = 10L;
    private static final int BATCH_SIZE = 50;
    private static final int IN_PROCESS_MAX_ATTEMPTS = 3;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationRetryableDelivery retryableDelivery;

    // Use a real Jackson 3 mapper so polymorphic round-trip is actually
    // exercised — the tasklet uses ObjectMapper.readValue and we want to
    // verify the deserialization path against a realistic mapper.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private RedeliverFailedNotificationsTasklet tasklet;

    private StepContribution stepContribution;
    private BaseTasklet.StepArguments stepArguments;

    @BeforeEach
    void setUp() {
        tasklet = new RedeliverFailedNotificationsTasklet(notificationRepository, retryableDelivery, objectMapper);
        ReflectionTestUtils.setField(tasklet, "cumulativeMaxAttempts", CUMULATIVE_MAX_ATTEMPTS);
        ReflectionTestUtils.setField(tasklet, "backoffMinutes", BACKOFF_MINUTES);
        ReflectionTestUtils.setField(tasklet, "batchSize", BATCH_SIZE);
        ReflectionTestUtils.setField(tasklet, "inProcessMaxAttempts", IN_PROCESS_MAX_ATTEMPTS);

        final JobInstance jobInstance = new JobInstance(1L, "REDELIVER_FAILED_NOTIFICATIONS_JOB");
        final JobExecution jobExecution = new JobExecution(1L, jobInstance, new JobParameters());
        final StepExecution stepExecution = new StepExecution("redeliverStep", jobExecution);
        stepContribution = new StepContribution(stepExecution);
        stepArguments = new BaseTasklet.StepArguments(
                "REDELIVER_FAILED_NOTIFICATIONS_JOB",
                LocalDateTime.now(),
                new JobParameters(),
                stepExecution
        );
    }

    private NotificationRedrivePayload validRedrivePayload() {
        return NotificationRedrivePayload.builder()
                .notificationType(NotificationType.SHIPPING_CONFIRMATION)
                .communicationChanel(CommunicationChanel.EMAIL)
                .subject("Your order has shipped")
                .templatePath("email/shipping-confirmation")
                .userContact(UserContactDto.builder()
                        .name("Jane")
                        .email("jane@example.com")
                        .phoneNumber("+33600000000")
                        .defaultCommunicationChanel(CommunicationChanel.EMAIL)
                        .build())
                .build();
    }

    private NotificationEntity entityWith(final int retryCount, final String payloadJson) {
        return NotificationEntity.builder()
                .notificationType(NotificationType.SHIPPING_CONFIRMATION)
                .communicationChannel(CommunicationChanel.EMAIL)
                .status(NotificationStatus.PENDING_RETRY)
                .recipient("jane@example.com")
                .retryCount(retryCount)
                .lastAttemptAt(LocalDateTime.now().minusHours(1))
                .payload(payloadJson)
                .build();
    }

    @Nested
    @DisplayName("Empty input")
    class EmptyInput {

        @Test
        @DisplayName("no candidates so returns FINISHED with COMPLETED, neither retryable nor save touched")
        void noCandidatesIsNoOp() throws Exception {
            when(notificationRepository.findRedrivable(
                    eq(NotificationStatus.PENDING_RETRY),
                    eq(CUMULATIVE_MAX_ATTEMPTS),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(Collections.emptyList());

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
            verifyNoInteractions(retryableDelivery);
            verify(notificationRepository, never()).save(any(NotificationEntity.class));
        }
    }

    @Nested
    @DisplayName("Repository query — backoff threshold and page size")
    class RepoQuery {

        @Test
        @DisplayName("query uses status=PENDING_RETRY and the configured cumulative cap")
        void queryUsesPendingRetryAndConfiguredCap() throws Exception {
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(Collections.emptyList());

            tasklet.execute(stepContribution, stepArguments);

            verify(notificationRepository).findRedrivable(
                    eq(NotificationStatus.PENDING_RETRY),
                    eq(CUMULATIVE_MAX_ATTEMPTS),
                    any(LocalDateTime.class),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("threshold is roughly now - backoffMinutes")
        void thresholdIsNowMinusBackoff() throws Exception {
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(Collections.emptyList());
            final LocalDateTime before = LocalDateTime.now();

            tasklet.execute(stepContribution, stepArguments);

            final ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(notificationRepository).findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    captor.capture(),
                    any(Pageable.class));
            final LocalDateTime threshold = captor.getValue();
            assertThat(threshold).isBetween(
                    before.minusMinutes(BACKOFF_MINUTES + 1),
                    LocalDateTime.now().minusMinutes(BACKOFF_MINUTES - 1));
        }

        @Test
        @DisplayName("pageable is built with the configured batch size")
        void pageableUsesConfiguredBatchSize() throws Exception {
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(Collections.emptyList());

            tasklet.execute(stepContribution, stepArguments);

            final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(notificationRepository).findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(BATCH_SIZE);
        }
    }

    @Nested
    @DisplayName("Happy path — single redrivable candidate, budget remaining")
    class HappyPath {

        @Test
        @DisplayName("invokes retryable delivery once and bumps coordination row, status stays PENDING_RETRY")
        void redrivesAndBumpsRow() throws Exception {
            final String payloadJson = objectMapper.writeValueAsString(validRedrivePayload());
            // retryCount=0 → after bump (3) still under cap (9) → PENDING_RETRY
            final NotificationEntity entity = entityWith(0, payloadJson);
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(List.of(entity));
            when(notificationRepository.save(any(NotificationEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            tasklet.execute(stepContribution, stepArguments);

            // The retryable bean must be invoked exactly once with a context
            // rebuilt from the persisted payload.
            final ArgumentCaptor<NotificationContext<?>> ctxCap = forNotificationContext();
            verify(retryableDelivery, times(1)).deliver(ctxCap.capture());
            final NotificationContext<?> ctx = ctxCap.getValue();
            assertThat(ctx.getNotificationType()).isEqualTo(NotificationType.SHIPPING_CONFIRMATION);
            assertThat(ctx.getCommunicationChanel()).isEqualTo(CommunicationChanel.EMAIL);
            assertThat(ctx.getUser().getEmail()).isEqualTo("jane@example.com");

            // The OLD coordination row is bumped: retryCount 0 + 3 = 3,
            // status remains PENDING_RETRY (still under the cap of 9).
            final ArgumentCaptor<NotificationEntity> saveCap = ArgumentCaptor.forClass(NotificationEntity.class);
            verify(notificationRepository).save(saveCap.capture());
            final NotificationEntity saved = saveCap.getValue();
            assertThat(saved.getRetryCount()).isEqualTo(IN_PROCESS_MAX_ATTEMPTS);
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING_RETRY);
            assertThat(saved.getLastAttemptAt()).isNotNull();

            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Cumulative cap reached")
    class CumulativeCap {

        @Test
        @DisplayName("retryCount + inProcessMax >= cumulativeMax → row flips to FAILED")
        void capReachedFlipsToFailed() throws Exception {
            final String payloadJson = objectMapper.writeValueAsString(validRedrivePayload());
            // retryCount=6, +3 = 9, hits cap → FAILED
            final NotificationEntity entity = entityWith(CUMULATIVE_MAX_ATTEMPTS - IN_PROCESS_MAX_ATTEMPTS, payloadJson);
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(List.of(entity));
            when(notificationRepository.save(any(NotificationEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            tasklet.execute(stepContribution, stepArguments);

            // Retryable bean still invoked — the row had budget when picked up.
            verify(retryableDelivery, times(1)).deliver(any(NotificationContext.class));

            final ArgumentCaptor<NotificationEntity> saveCap = ArgumentCaptor.forClass(NotificationEntity.class);
            verify(notificationRepository).save(saveCap.capture());
            final NotificationEntity saved = saveCap.getValue();
            assertThat(saved.getRetryCount()).isEqualTo(CUMULATIVE_MAX_ATTEMPTS);
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("Corrupted payload")
    class CorruptedPayload {

        @Test
        @DisplayName("payload column is null so retryable NEVER invoked, row goes straight to FAILED with errorMessage")
        void nullPayloadGoesToFailed() throws Exception {
            final NotificationEntity entity = entityWith(0, null);
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(List.of(entity));
            when(notificationRepository.save(any(NotificationEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            tasklet.execute(stepContribution, stepArguments);

            verifyNoInteractions(retryableDelivery);

            final ArgumentCaptor<NotificationEntity> saveCap = ArgumentCaptor.forClass(NotificationEntity.class);
            verify(notificationRepository).save(saveCap.capture());
            final NotificationEntity saved = saveCap.getValue();
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(saved.getErrorMessage()).isEqualTo("redrive payload missing/corrupted");
            assertThat(saved.getLastAttemptAt()).isNotNull();
        }

        @Test
        @DisplayName("payload column is unparseable JSON so retryable NEVER invoked, row goes straight to FAILED")
        void unparseablePayloadGoesToFailed() throws Exception {
            final NotificationEntity entity = entityWith(0, "{not-valid-json");
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(List.of(entity));
            when(notificationRepository.save(any(NotificationEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            tasklet.execute(stepContribution, stepArguments);

            verifyNoInteractions(retryableDelivery);

            final ArgumentCaptor<NotificationEntity> saveCap = ArgumentCaptor.forClass(NotificationEntity.class);
            verify(notificationRepository).save(saveCap.capture());
            final NotificationEntity saved = saveCap.getValue();
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(saved.getErrorMessage()).isEqualTo("redrive payload missing/corrupted");
        }

        @Test
        @DisplayName("blank payload also goes straight to FAILED")
        void blankPayloadGoesToFailed() throws Exception {
            final NotificationEntity entity = entityWith(0, "   ");
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(List.of(entity));
            when(notificationRepository.save(any(NotificationEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            tasklet.execute(stepContribution, stepArguments);

            verifyNoInteractions(retryableDelivery);
            final ArgumentCaptor<NotificationEntity> saveCap = ArgumentCaptor.forClass(NotificationEntity.class);
            verify(notificationRepository).save(saveCap.capture());
            assertThat(saveCap.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<NotificationContext<?>> forNotificationContext() {
        // Mockito loses generic info; the unchecked cast keeps the captor
        // typed at the call site without the verbose ArgumentCaptor.forClass
        // dance per assertion.
        return (ArgumentCaptor) ArgumentCaptor.forClass(NotificationContext.class);
    }
}
