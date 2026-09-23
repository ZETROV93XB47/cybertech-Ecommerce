package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.repositories.OrderRepository;
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
 * Unit tests for {@link GetAllFailedPaymentOrderTasklet}.
 *
 * <p>Pins the fix: the tasklet queries orders CURRENTLY {@link OrderStatus#PAYMENT_FAILED}
 * (via {@link OrderRepository#findByStatus}), not payment-attempt history — a retried/completed
 * order must never resurface in the "please retry your payment" reminder.
 */
@ExtendWith(MockitoExtension.class)
class GetAllFailedPaymentOrderTaskletTest {

    @Mock
    private OrderRepository orderRepository;

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

    private OrderEntity paymentFailedOrderForUser(final String email) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().email(email).build();
        return OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(user)
                .uuid(UUID.randomUUID())
                .status(OrderStatus.PAYMENT_FAILED)
                .build();
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("groups PAYMENT_FAILED order UUIDs by user email and writes to JobExecutionContext")
        void groupsFailedPaymentsByUserEmail() throws Exception {
            final OrderEntity o1 = paymentFailedOrderForUser("a@example.com");
            final OrderEntity o2 = paymentFailedOrderForUser("a@example.com");
            final OrderEntity o3 = paymentFailedOrderForUser("b@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAYMENT_FAILED))
                    .thenReturn(List.of(o1, o2, o3));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
            final Map<String, List<UUID>> grouped = (Map<String, List<UUID>>) jobExecution.getExecutionContext().get(FAILED_PAYMENT_ORDERS_MAP_BY_USERS);
            assertThat(grouped).hasSize(2);
            assertThat(grouped.get("a@example.com")).containsExactlyInAnyOrder(o1.getUuid(), o2.getUuid());
            assertThat(grouped.get("b@example.com")).containsExactly(o3.getUuid());
        }

        @Test
        @DisplayName("queries the repository with OrderStatus.PAYMENT_FAILED (current status, not payment-attempt history)")
        void queriesRepoWithPaymentFailedOrderStatus() throws Exception {
            when(orderRepository.findByStatus(OrderStatus.PAYMENT_FAILED))
                    .thenReturn(Collections.emptyList());

            tasklet.execute(stepContribution, stepArguments);

            verify(orderRepository).findByStatus(OrderStatus.PAYMENT_FAILED);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("single user with single PAYMENT_FAILED order → one-entry map")
        void singleEntryMap() throws Exception {
            final OrderEntity o1 = paymentFailedOrderForUser("only@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAYMENT_FAILED))
                    .thenReturn(List.of(o1));

            tasklet.execute(stepContribution, stepArguments);

            final Map<String, List<UUID>> grouped = (Map<String, List<UUID>>) jobExecution.getExecutionContext().get(FAILED_PAYMENT_ORDERS_MAP_BY_USERS);
            assertThat(grouped).hasSize(1).containsKey("only@example.com");
            assertThat(grouped.get("only@example.com")).containsExactly(o1.getUuid());
        }
    }

    @Nested
    @DisplayName("Empty input")
    class EmptyInput {

        @Test
        @DisplayName("no PAYMENT_FAILED orders → ExitStatus.exitCode == NO_FAILED_PAYMENT_ORDER_FOUND, nothing written")
        void noFailures_isNoOp() throws Exception {
            when(orderRepository.findByStatus(OrderStatus.PAYMENT_FAILED))
                    .thenReturn(Collections.emptyList());

            tasklet.execute(stepContribution, stepArguments);

            assertThat(stepContribution.getExitStatus().getExitCode()).isEqualTo(NO_FAILED_PAYMENT_ORDER_FOUND);
            assertThat(jobExecution.getExecutionContext().containsKey(FAILED_PAYMENT_ORDERS_MAP_BY_USERS)).isFalse();
        }

        @Test
        @DisplayName("returns FINISHED on empty repository result")
        void returnsFinishedOnEmpty() throws Exception {
            when(orderRepository.findByStatus(OrderStatus.PAYMENT_FAILED))
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
            when(orderRepository.findByStatus(OrderStatus.PAYMENT_FAILED))
                    .thenThrow(new RuntimeException("DB unavailable"));

            assertThatThrownBy(() -> tasklet.execute(stepContribution, stepArguments))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB unavailable");
        }

        @Test
        @DisplayName("an order with a missing user entity NPEs (defensive coverage)")
        void orderWithoutUserNpe() {
            final OrderEntity broken = OrderEntityBuilder.aValidOrderBuilder()
                    .status(OrderStatus.PAYMENT_FAILED)
                    .userEntity(null)
                    .build();
            when(orderRepository.findByStatus(OrderStatus.PAYMENT_FAILED))
                    .thenReturn(List.of(broken));

            assertThatThrownBy(() -> tasklet.execute(stepContribution, stepArguments))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
