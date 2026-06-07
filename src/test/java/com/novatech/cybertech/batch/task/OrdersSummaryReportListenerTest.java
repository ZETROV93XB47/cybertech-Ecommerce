package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.dto.data.EmailDto;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.services.core.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.FAILED_PAYMENT_ORDERS_MAP_BY_USERS;
import static com.novatech.cybertech.constants.CyberTechAppConstants.PENDING_ORDERS_MAP_BY_USER_EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link OrdersSummaryReportListener}.
 *
 * Per progress.md:
 * <ul>
 *   <li>BUG-112 (open): unchecked raw casts on JobExecutionContext entries.</li>
 *   <li>BUG-113 (open): no per-recipient try/catch around mailService.sendEmail.</li>
 * </ul>
 *
 * BatchStatus matrix: {@link BatchStatus#isUnsuccessful()} returns true for FAILED, ABANDONED,
 * and UNKNOWN. COMPLETED, STOPPED, STARTED, etc. are NOT unsuccessful — emails are sent.
 */
@ExtendWith(MockitoExtension.class)
class OrdersSummaryReportListenerTest {

    private static final String EMAIL_SENDER = "noreply@cybertech.example";

    @Mock
    private MailService mailService;

    @Mock
    private SpringTemplateEngine templateEngine;

    @InjectMocks
    private OrdersSummaryReportListener listener;

    private JobExecution jobExecution;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "emailSender", EMAIL_SENDER);
        final JobInstance jobInstance = new JobInstance(1L, "REPORT_JOB");
        jobExecution = new JobExecution(1L, jobInstance, new JobParameters());
        jobExecution.setStatus(BatchStatus.COMPLETED);
    }

    private void putCancelled(final Map<String, List<UUID>> map) {
        jobExecution.getExecutionContext().put(PENDING_ORDERS_MAP_BY_USER_EMAIL, map);
    }

    private void putPending(final Map<String, List<UUID>> map) {
        jobExecution.getExecutionContext().put(FAILED_PAYMENT_ORDERS_MAP_BY_USERS, map);
    }

    @Nested
    @DisplayName("Successful job — emails are sent")
    class SuccessfulJob {

        @Test
        @DisplayName("COMPLETED + neither map present → no emails sent")
        void noMaps_noEmails() {
            jobExecution.setStatus(BatchStatus.COMPLETED);

            listener.afterJob(jobExecution);

            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("COMPLETED + only cancelled map → ORDER_CANCELLATION emails per user")
        void cancelledMap_sendsCancellationEmails() {
            final UUID u1 = UUID.randomUUID();
            putCancelled(Map.of("a@example.com", List.of(u1)));

            listener.afterJob(jobExecution);

            final ArgumentCaptor<EmailDto> captor = ArgumentCaptor.forClass(EmailDto.class);
            verify(mailService, times(1)).sendEmail(captor.capture());
            final EmailDto sent = captor.getValue();
            assertThat(sent.getTo()).isEqualTo("a@example.com");
            assertThat(sent.getFrom()).isEqualTo(EMAIL_SENDER);
            assertThat(sent.getSubject()).isEqualTo(EmailTemplateType.ORDER_CANCELLATION.getNotificationSubject().getSubject());
            assertThat(sent.getTemplatePath()).isEqualTo(EmailTemplateType.ORDER_CANCELLATION.getTemplatePath());
            assertThat(sent.getTemplateVariables()).containsEntry("orderId", List.of(u1));
        }

        @Test
        @DisplayName("COMPLETED + only pending-payment map → ORDER_PENDING_PAYMENT emails per user")
        void pendingMap_sendsPendingPaymentEmails() {
            final UUID u1 = UUID.randomUUID();
            putPending(Map.of("z@example.com", List.of(u1)));

            listener.afterJob(jobExecution);

            final ArgumentCaptor<EmailDto> captor = ArgumentCaptor.forClass(EmailDto.class);
            verify(mailService, times(1)).sendEmail(captor.capture());
            final EmailDto sent = captor.getValue();
            assertThat(sent.getTo()).isEqualTo("z@example.com");
            assertThat(sent.getSubject()).isEqualTo(EmailTemplateType.ORDER_PENDING_PAYMENT.getNotificationSubject().getSubject());
            assertThat(sent.getTemplatePath()).isEqualTo(EmailTemplateType.ORDER_PENDING_PAYMENT.getTemplatePath());
        }

        @Test
        @DisplayName("COMPLETED + both maps populated → emails for both groups, one per (user, group) pair")
        void bothMaps_sendsBothGroups() {
            putCancelled(Map.of("c1@example.com", List.of(UUID.randomUUID())));
            putPending(Map.of("p1@example.com", List.of(UUID.randomUUID()), "p2@example.com", List.of(UUID.randomUUID())));

            listener.afterJob(jobExecution);

            verify(mailService, times(3)).sendEmail(any(EmailDto.class));
        }

        @Test
        @DisplayName("empty cancelled map → no cancellation email but still iterates pending map")
        void emptyMap_noEmail() {
            putCancelled(Map.of());

            listener.afterJob(jobExecution);

            verify(mailService, never()).sendEmail(any(EmailDto.class));
        }
    }

    @Nested
    @DisplayName("BatchStatus matrix — isUnsuccessful() contract")
    class BatchStatusMatrix {

        @Test
        @DisplayName("FAILED → no emails sent")
        void failedJob_skipsEmails() {
            jobExecution.setStatus(BatchStatus.FAILED);
            putCancelled(Map.of("a@example.com", List.of(UUID.randomUUID())));

            listener.afterJob(jobExecution);

            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("ABANDONED → no emails sent")
        void abandonedJob_skipsEmails() {
            jobExecution.setStatus(BatchStatus.ABANDONED);
            putCancelled(Map.of("a@example.com", List.of(UUID.randomUUID())));

            listener.afterJob(jobExecution);

            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("UNKNOWN → no emails sent")
        void unknownJob_skipsEmails() {
            jobExecution.setStatus(BatchStatus.UNKNOWN);
            putCancelled(Map.of("a@example.com", List.of(UUID.randomUUID())));

            listener.afterJob(jobExecution);

            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("COMPLETED → emails ARE sent")
        void completedJob_sendsEmails() {
            jobExecution.setStatus(BatchStatus.COMPLETED);
            putCancelled(Map.of("a@example.com", List.of(UUID.randomUUID())));

            listener.afterJob(jobExecution);

            verify(mailService, times(1)).sendEmail(any(EmailDto.class));
        }

        /**
         * BUG-112 byline: {@link BatchStatus#STOPPED}.isUnsuccessful() == false, so emails ARE sent
         * even when the job was stopped on user request — likely undesired but this is the
         * documented current behaviour; pinned green.
         */
        @Test
        @DisplayName("BUG-112 (LOW): STOPPED status still triggers emails because isUnsuccessful() is false for STOPPED")
        void stoppedJob_stillSendsEmails_documentsBatchStatusContract() {
            jobExecution.setStatus(BatchStatus.STOPPED);
            putCancelled(Map.of("a@example.com", List.of(UUID.randomUUID())));

            listener.afterJob(jobExecution);

            verify(mailService, times(1)).sendEmail(any(EmailDto.class));
        }
    }

    @Nested
    @DisplayName("BUG-112 — unchecked raw casts on execution-context entries")
    class UncheckedCasts {

        @Test
        @DisplayName("BUG-112 (LOW): a non-Map value under PENDING_ORDERS_MAP_BY_USER_EMAIL surfaces a ClassCastException at email-composition time")
        void wrongTypeUnderCancelledKey_classCast() {
            jobExecution.setStatus(BatchStatus.COMPLETED);
            jobExecution.getExecutionContext().put(PENDING_ORDERS_MAP_BY_USER_EMAIL, "not-a-map");

            assertThatThrownBy(() -> listener.afterJob(jobExecution))
                    .isInstanceOf(ClassCastException.class);
        }

        @Test
        @DisplayName("BUG-112 (LOW): a non-Map value under FAILED_PAYMENT_ORDERS_MAP_BY_USERS surfaces a ClassCastException")
        void wrongTypeUnderPendingKey_classCast() {
            jobExecution.setStatus(BatchStatus.COMPLETED);
            jobExecution.getExecutionContext().put(FAILED_PAYMENT_ORDERS_MAP_BY_USERS, 42);

            assertThatThrownBy(() -> listener.afterJob(jobExecution))
                    .isInstanceOf(ClassCastException.class);
        }
    }

    @Nested
    @DisplayName("BUG-113 — no per-recipient try/catch around mailService.sendEmail")
    class FailurePropagation {

        @Test
        @DisplayName("BUG-113 (LOW): the first SMTP failure aborts remaining recipients AND propagates out of afterJob")
        void mailServiceThrows_bubblesUp_abortsFurtherRecipients() {
            putCancelled(Map.of(
                    "a@example.com", List.of(UUID.randomUUID()),
                    "b@example.com", List.of(UUID.randomUUID()),
                    "c@example.com", List.of(UUID.randomUUID())
            ));
            doThrow(new RuntimeException("SMTP refused"))
                    .when(mailService).sendEmail(any(EmailDto.class));

            assertThatThrownBy(() -> listener.afterJob(jobExecution))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("SMTP refused");

            // Only one attempt happened (the first one threw), proving the loop has no per-item try/catch.
            verify(mailService, times(1)).sendEmail(any(EmailDto.class));
        }
    }
}
