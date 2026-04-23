package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.PaymentEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.repositories.PaymentAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.FAILED_PAYMENT_ORDERS_MAP_BY_USERS;
import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_FAILED_PAYMENT_ORDER_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetAllFailedPaymentOrderTasklet}. Pure read + group-by-user.
 */
@ExtendWith(MockitoExtension.class)
class GetAllFailedPaymentOrderTaskletTest {

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @InjectMocks
    private GetAllFailedPaymentOrderTasklet tasklet;

    private StepContribution stepContribution;
    private JobExecution jobExecution;
    private BaseTasklet.StepArguments stepArguments;

    @BeforeEach
    void setUp() {
        final JobInstance jobInstance = new JobInstance(1L, "REPORT_JOB");
        jobExecution = new JobExecution(1L, jobInstance, new JobParameters());
        final StepExecution stepExecution = new StepExecution("getFailedPaymentStep", jobExecution);
        stepContribution = new StepContribution(stepExecution);
        stepArguments = new BaseTasklet.StepArguments(
                "REPORT_JOB",
                LocalDateTime.now(),
                new JobParameters(),
                stepExecution
        );
    }

    private PaymentEntity failedPaymentForUser(final String email) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().email(email).build();
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder().userEntity(user).uuid(UUID.randomUUID()).build();
        return PaymentEntityBuilder.aValidPaymentBuilder()
                .status(PaymentAttemptStatus.FAILED)
                .orderEntity(order)
                .build();
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("groups failed-payment order UUIDs by user email and writes to JobExecutionContext")
        void groupsFailedPaymentsByUserEmail() throws Exception {
            final PaymentEntity p1 = failedPaymentForUser("a@example.com");
            final PaymentEntity p2 = failedPaymentForUser("a@example.com");
            final PaymentEntity p3 = failedPaymentForUser("b@example.com");
            when(paymentAttemptRepository.findByStatus(PaymentAttemptStatus.FAILED))
                    .thenReturn(List.of(p1, p2, p3));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
            final Map<String, List<UUID>> grouped = (Map<String, List<UUID>>) jobExecution.getExecutionContext().get(FAILED_PAYMENT_ORDERS_MAP_BY_USERS);
            assertThat(grouped).hasSize(2);
            assertThat(grouped.get("a@example.com")).containsExactlyInAnyOrder(p1.getOrderEntity().getUuid(), p2.getOrderEntity().getUuid());
            assertThat(grouped.get("b@example.com")).containsExactly(p3.getOrderEntity().getUuid());
        }

        @Test
        @DisplayName("queries the repository with PaymentAttemptStatus.FAILED")
        void queriesRepoWithFailedStatus() throws Exception {
            when(paymentAttemptRepository.findByStatus(PaymentAttemptStatus.FAILED))
                    .thenReturn(Collections.emptyList());

            tasklet.execute(stepContribution, stepArguments);

            verify(paymentAttemptRepository).findByStatus(PaymentAttemptStatus.FAILED);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("single user with single failed payment → one-entry map")
        void singleEntryMap() throws Exception {
            final PaymentEntity p1 = failedPaymentForUser("only@example.com");
            when(paymentAttemptRepository.findByStatus(PaymentAttemptStatus.FAILED))
                    .thenReturn(List.of(p1));

            tasklet.execute(stepContribution, stepArguments);

            final Map<String, List<UUID>> grouped = (Map<String, List<UUID>>) jobExecution.getExecutionContext().get(FAILED_PAYMENT_ORDERS_MAP_BY_USERS);
            assertThat(grouped).hasSize(1).containsKey("only@example.com");
            assertThat(grouped.get("only@example.com")).containsExactly(p1.getOrderEntity().getUuid());
        }
    }

    @Nested
    @DisplayName("Empty input")
    class EmptyInput {

        @Test
        @DisplayName("no failed payments → ExitStatus.exitCode == NO_FAILED_PAYMENT_ORDER_FOUND, nothing written")
        void noFailures_isNoOp() throws Exception {
            when(paymentAttemptRepository.findByStatus(PaymentAttemptStatus.FAILED))
                    .thenReturn(Collections.emptyList());

            tasklet.execute(stepContribution, stepArguments);

            assertThat(stepContribution.getExitStatus().getExitCode()).isEqualTo(NO_FAILED_PAYMENT_ORDER_FOUND);
            assertThat(jobExecution.getExecutionContext().containsKey(FAILED_PAYMENT_ORDERS_MAP_BY_USERS)).isFalse();
        }

        @Test
        @DisplayName("returns FINISHED on empty repository result")
        void returnsFinishedOnEmpty() throws Exception {
            when(paymentAttemptRepository.findByStatus(PaymentAttemptStatus.FAILED))
                    .thenReturn(Collections.emptyList());

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        }
    }

    @Nested
    @DisplayName("Failure paths")
    class FailurePaths {

        @Test
        @DisplayName("repository failure bubbles out from typed execute()")
        void repositoryThrows_bubbles() {
            when(paymentAttemptRepository.findByStatus(PaymentAttemptStatus.FAILED))
                    .thenThrow(new RuntimeException("DB unavailable"));

            assertThatThrownBy(() -> tasklet.execute(stepContribution, stepArguments))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB unavailable");
        }

        @Test
        @DisplayName("a payment with a missing order entity NPEs (defensive coverage)")
        void paymentWithoutOrderNpe() {
            final PaymentEntity broken = PaymentEntityBuilder.aValidPaymentBuilder()
                    .status(PaymentAttemptStatus.FAILED)
                    .orderEntity(null)
                    .build();
            when(paymentAttemptRepository.findByStatus(PaymentAttemptStatus.FAILED))
                    .thenReturn(List.of(broken));

            assertThatThrownBy(() -> tasklet.execute(stepContribution, stepArguments))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
