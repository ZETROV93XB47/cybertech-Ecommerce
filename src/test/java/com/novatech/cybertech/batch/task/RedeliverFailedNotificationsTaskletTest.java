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
import static org.mockito.Mockito.doAnswer;
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
 *
 * <p>{@link NotificationRetryableDelivery#redeliver(NotificationContext, NotificationEntity)}
 * is mocked — its real bump/persist behaviour is {@link NotificationRetryableDeliveryImpTest}'s
 * responsibility. Here, a {@code doAnswer} on the mock mutates the same {@code entity} instance
 * the tasklet passed in (exactly like the real implementation would, via
 * {@code NotificationOutcomeRecorder#updateOutcome}), so these tests can verify the tasklet's OWN
 * decision logic in isolation: does it promote to terminal {@code FAILED} at the right moment,
 * and does it correctly leave a just-{@code SENT} row alone.
 *
 * <p>Covers:
 * <ol>
 *   <li>No candidates returned → no-op, no recorder/retryable interactions.</li>
 *   <li>Single redrivable candidate, still under budget after redrive → stays PENDING_RETRY,
 *       tasklet does not save it again (persistence is the retryable bean's job).</li>
 *   <li>Cumulative cap reached after redrive → tasklet promotes the row to FAILED and saves it.</li>
 *   <li>Redrive succeeds (SENT) → tasklet never promotes it to FAILED, even if retryCount happens
 *       to be at/above the cap.</li>
 *   <li>Corrupted/null payload → row goes straight to FAILED, retryable bean NEVER invoked.</li>
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

    /**
     * Stubs {@code retryableDelivery.redeliver(any, entity)} to mutate {@code entity} in place —
     * exactly what the real {@code NotificationRetryableDeliveryImp} does via
     * {@code NotificationOutcomeRecorder#updateOutcome} — so the tasklet's own promotion logic can
     * be exercised without pulling in that bean's real implementation.
     */
    private void stubRedeliverOutcome(final NotificationEntity entity, final NotificationStatus resultingStatus, final int resultingRetryCount) {
        doAnswer(inv -> {
            entity.setStatus(resultingStatus);
            entity.setRetryCount(resultingRetryCount);
            entity.setLastAttemptAt(LocalDateTime.now());
            return null;
        }).when(retryableDelivery).redeliver(any(NotificationContext.class), eq(entity));
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
        @DisplayName("invokes redeliver on the SAME row (no new row); still under cap so tasklet does not save it again")
        void redrivesInPlace_noPromotion() throws Exception {
            final String payloadJson = objectMapper.writeValueAsString(validRedrivePayload());
            // retryCount=0 → redeliver bumps it to 3 (still under cap 9) → PENDING_RETRY
            final NotificationEntity entity = entityWith(0, payloadJson);
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(List.of(entity));
            stubRedeliverOutcome(entity, NotificationStatus.PENDING_RETRY, IN_PROCESS_MAX_ATTEMPTS);

            tasklet.execute(stepContribution, stepArguments);

            // redeliver() is called on the SAME entity instance — one row, not a new one.
            final ArgumentCaptor<NotificationContext<?>> ctxCap = forNotificationContext();
            verify(retryableDelivery, times(1)).redeliver(ctxCap.capture(), eq(entity));
            final NotificationContext<?> ctx = ctxCap.getValue();
            assertThat(ctx.getNotificationType()).isEqualTo(NotificationType.SHIPPING_CONFIRMATION);
            assertThat(ctx.getCommunicationChanel()).isEqualTo(CommunicationChanel.EMAIL);
            assertThat(ctx.getUser().getEmail()).isEqualTo("jane@example.com");

            // Still under the cumulative cap after the bump — the tasklet itself never calls
            // save() here; persisting the bumped state is entirely the retryable bean's job
            // (mocked away above).
            verify(notificationRepository, never()).save(any(NotificationEntity.class));
            assertThat(entity.getRetryCount()).isEqualTo(IN_PROCESS_MAX_ATTEMPTS);
            assertThat(entity.getStatus()).isEqualTo(NotificationStatus.PENDING_RETRY);

            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Cumulative cap reached")
    class CumulativeCap {

        @Test
        @DisplayName("redeliver leaves the row PENDING_RETRY at/above the cap → tasklet promotes it to FAILED")
        void capReachedFlipsToFailed() throws Exception {
            final String payloadJson = objectMapper.writeValueAsString(validRedrivePayload());
            final NotificationEntity entity = entityWith(CUMULATIVE_MAX_ATTEMPTS - IN_PROCESS_MAX_ATTEMPTS, payloadJson);
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(List.of(entity));
            when(notificationRepository.save(any(NotificationEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            // redeliver bumps retryCount to exactly the cap, still failing (PENDING_RETRY).
            stubRedeliverOutcome(entity, NotificationStatus.PENDING_RETRY, CUMULATIVE_MAX_ATTEMPTS);

            tasklet.execute(stepContribution, stepArguments);

            verify(retryableDelivery, times(1)).redeliver(any(NotificationContext.class), eq(entity));

            // The tasklet notices the cap was crossed and promotes + saves the SAME row.
            final ArgumentCaptor<NotificationEntity> saveCap = ArgumentCaptor.forClass(NotificationEntity.class);
            verify(notificationRepository, times(1)).save(saveCap.capture());
            final NotificationEntity saved = saveCap.getValue();
            assertThat(saved).isSameAs(entity);
            assertThat(saved.getRetryCount()).isEqualTo(CUMULATIVE_MAX_ATTEMPTS);
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        }

        @Test
        @DisplayName("redeliver succeeds (SENT) even though retryCount is at/above the cap → NOT promoted to FAILED")
        void successfulRedriveIsNeverPromotedToFailed() throws Exception {
            // Regression test for a prior design flaw: the coordination row used to get bumped
            // (and potentially flipped to FAILED) unconditionally after every redrive tick, even
            // one that just succeeded — leaving a SENT notification's row marked FAILED.
            final String payloadJson = objectMapper.writeValueAsString(validRedrivePayload());
            final NotificationEntity entity = entityWith(CUMULATIVE_MAX_ATTEMPTS - IN_PROCESS_MAX_ATTEMPTS, payloadJson);
            when(notificationRepository.findRedrivable(
                    any(NotificationStatus.class),
                    any(Integer.class),
                    any(LocalDateTime.class),
                    any(Pageable.class))).thenReturn(List.of(entity));
            // redeliver succeeds this time — status flips to SENT.
            stubRedeliverOutcome(entity, NotificationStatus.SENT, entity.getRetryCount());

            tasklet.execute(stepContribution, stepArguments);

            verify(retryableDelivery, times(1)).redeliver(any(NotificationContext.class), eq(entity));
            // The tasklet must never touch a row that just succeeded.
            verify(notificationRepository, never()).save(any(NotificationEntity.class));
            assertThat(entity.getStatus()).isEqualTo(NotificationStatus.SENT);
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
            assertThat(saved.getErrorHistory()).isEqualTo("redrive payload missing/corrupted");
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
            assertThat(saved.getErrorHistory()).isEqualTo("redrive payload missing/corrupted");
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
