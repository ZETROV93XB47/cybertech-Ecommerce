package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.StockService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_ORDERS_TO_CANCEL;
import static com.novatech.cybertech.constants.CyberTechAppConstants.PENDING_ORDERS_MAP_BY_USER_EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CancelAllPendingOrdersByTimeTasklet}.
 *
 * Per progress.md (SA4.3R / F2): BUG-111 fix lands as a per-order try/catch around
 * {@code stockService.releaseStock(...)}. We verify it green here.
 *
 * Test strategy: bypass {@link BaseTasklet#execute(StepContribution, org.springframework.batch.core.scope.context.ChunkContext)}
 * (which uses ScopedValue) by calling the typed
 * {@link CancelAllPendingOrdersByTimeTasklet#execute(StepContribution, BaseTasklet.StepArguments)} overload directly.
 */
@ExtendWith(MockitoExtension.class)
class CancelAllPendingOrdersByTimeTaskletTest {

    private static final int TIME_BEFORE_DELETING_ORDER = 7;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StockService stockService;

    @InjectMocks
    private CancelAllPendingOrdersByTimeTasklet tasklet;

    private StepContribution stepContribution;
    private JobExecution jobExecution;
    private BaseTasklet.StepArguments stepArguments;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tasklet, "timeBeforeDeletingOrder", TIME_BEFORE_DELETING_ORDER);

        final JobInstance jobInstance = new JobInstance(1L, "TEST_JOB");
        jobExecution = new JobExecution(1L, jobInstance, new JobParameters());
        final StepExecution stepExecution = new StepExecution("cancelStep", jobExecution);
        stepContribution = new StepContribution(stepExecution);

        stepArguments = new BaseTasklet.StepArguments(
                "TEST_JOB",
                LocalDateTime.now(),
                new JobParameters(),
                stepExecution
        );
    }

    private OrderEntity orderForUser(final String email) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().email(email).build();
        return OrderEntityBuilder.aValidOrderBuilder()
                .uuid(UUID.randomUUID())
                .status(OrderStatus.PAYMENT_FAILED)
                .userEntity(user)
                .build();
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("returns FINISHED RepeatStatus on every invocation")
        void returnsFinished() throws Exception {
            when(orderRepository.findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        }

        @Test
        @DisplayName("queries OrderRepository.findByStatusAndOrderDateBefore with PAYMENT_FAILED + cutoff in past")
        void queriesRepositoryWithCorrectArgs() throws Exception {
            when(orderRepository.findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            tasklet.execute(stepContribution, stepArguments);

            verify(orderRepository).findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("releases stock and flips status=CANCELED for each pending order")
        void cancelsEachPendingOrder() throws Exception {
            final OrderEntity o1 = orderForUser("a@example.com");
            final OrderEntity o2 = orderForUser("a@example.com");
            final OrderEntity o3 = orderForUser("b@example.com");
            when(orderRepository.findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class)))
                    .thenReturn(List.of(o1, o2, o3));

            tasklet.execute(stepContribution, stepArguments);

            verify(stockService).releaseStock(o1.getUuid());
            verify(stockService).releaseStock(o2.getUuid());
            verify(stockService).releaseStock(o3.getUuid());
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(o2.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(o3.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("groups cancelled-order UUIDs by user email and writes them to the JobExecutionContext")
        void writesGroupedMapToExecutionContext() throws Exception {
            final OrderEntity o1 = orderForUser("a@example.com");
            final OrderEntity o2 = orderForUser("a@example.com");
            final OrderEntity o3 = orderForUser("b@example.com");
            when(orderRepository.findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class)))
                    .thenReturn(List.of(o1, o2, o3));

            tasklet.execute(stepContribution, stepArguments);

            final Object raw = jobExecution.getExecutionContext().get(PENDING_ORDERS_MAP_BY_USER_EMAIL);
            assertThat(raw).isInstanceOf(Map.class);
            final Map<String, List<UUID>> grouped = (Map<String, List<UUID>>) raw;
            assertThat(grouped).hasSize(2);
            assertThat(grouped.get("a@example.com")).containsExactlyInAnyOrder(o1.getUuid(), o2.getUuid());
            assertThat(grouped.get("b@example.com")).containsExactly(o3.getUuid());
        }

        @Test
        @DisplayName("sets ExitStatus = COMPLETED when at least one order was cancelled")
        void setsCompletedExitStatusOnSuccess() throws Exception {
            final OrderEntity o1 = orderForUser("a@example.com");
            when(orderRepository.findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class)))
                    .thenReturn(List.of(o1));

            tasklet.execute(stepContribution, stepArguments);

            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Empty input")
    class EmptyInput {

        @Test
        @DisplayName("no orders → ExitStatus.exitCode == NO_ORDERS_TO_CANCEL, no service calls")
        void noOrdersIsNoOp() throws Exception {
            when(orderRepository.findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            tasklet.execute(stepContribution, stepArguments);

            assertThat(stepContribution.getExitStatus().getExitCode()).isEqualTo(NO_ORDERS_TO_CANCEL);
            verifyNoInteractions(stockService);
            assertThat(jobExecution.getExecutionContext().containsKey(PENDING_ORDERS_MAP_BY_USER_EMAIL)).isFalse();
        }
    }

    @Nested
    @DisplayName("Failure paths — BUG-111 fix verification")
    class FailurePaths {

        @Test
        @DisplayName("BUG-111 (FIXED in F2): single releaseStock failure is isolated; subsequent orders are still cancelled")
        void singleReleaseStockFailure_doesNotAbortRemaining() throws Exception {
            final OrderEntity o1 = orderForUser("a@example.com");
            final OrderEntity o2 = orderForUser("b@example.com");
            final OrderEntity o3 = orderForUser("c@example.com");
            when(orderRepository.findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class)))
                    .thenReturn(List.of(o1, o2, o3));
            // Throw only for o2; succeed for o1 and o3
            org.mockito.Mockito.doAnswer(invocation -> {
                final UUID uuid = invocation.getArgument(0);
                if (uuid.equals(o2.getUuid())) {
                    throw new RuntimeException("Redis is down");
                }
                return null;
            }).when(stockService).releaseStock(any(UUID.class));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            verify(stockService).releaseStock(o1.getUuid());
            verify(stockService).releaseStock(o2.getUuid());
            verify(stockService).releaseStock(o3.getUuid());
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(o2.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED); // skipped
            assertThat(o3.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("BUG-111 (FIXED): the failed order is excluded from the user→uuid map written to context")
        void failedOrderIsNotInExecutionContextMap() throws Exception {
            final OrderEntity o1 = orderForUser("a@example.com");
            final OrderEntity o2 = orderForUser("a@example.com");
            when(orderRepository.findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class)))
                    .thenReturn(List.of(o1, o2));
            org.mockito.Mockito.doAnswer(invocation -> {
                final UUID uuid = invocation.getArgument(0);
                if (uuid.equals(o1.getUuid())) {
                    throw new RuntimeException("boom");
                }
                return null;
            }).when(stockService).releaseStock(any(UUID.class));

            tasklet.execute(stepContribution, stepArguments);

            final Map<String, List<UUID>> grouped = (Map<String, List<UUID>>) jobExecution.getExecutionContext().get(PENDING_ORDERS_MAP_BY_USER_EMAIL);
            assertThat(grouped.get("a@example.com")).containsExactly(o2.getUuid()).doesNotContain(o1.getUuid());
        }

        @Test
        @DisplayName("BUG-111 (FIXED): every order causes releaseStock to throw → no exception bubbles, FINISHED returned, no order cancelled")
        void everyReleaseStockFails_returnsFinishedAndNoCancellations() throws Exception {
            final OrderEntity o1 = orderForUser("a@example.com");
            final OrderEntity o2 = orderForUser("b@example.com");
            when(orderRepository.findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class)))
                    .thenReturn(List.of(o1, o2));
            doThrow(new RuntimeException("fail")).when(stockService).releaseStock(any(UUID.class));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            assertThat(o2.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }

        @Test
        @DisplayName("BUG-111 (FIXED): when all orders fail, the resulting empty map is still written to the JobExecutionContext")
        void allFailures_writesEmptyMap() throws Exception {
            final OrderEntity o1 = orderForUser("a@example.com");
            when(orderRepository.findByStatusAndOrderDateBefore(eq(OrderStatus.PAYMENT_FAILED), any(LocalDateTime.class)))
                    .thenReturn(List.of(o1));
            doThrow(new RuntimeException("boom")).when(stockService).releaseStock(o1.getUuid());

            tasklet.execute(stepContribution, stepArguments);

            assertThat(jobExecution.getExecutionContext().containsKey(PENDING_ORDERS_MAP_BY_USER_EMAIL)).isTrue();
            @SuppressWarnings("unchecked")
            final Map<String, List<UUID>> grouped = (Map<String, List<UUID>>) jobExecution.getExecutionContext().get(PENDING_ORDERS_MAP_BY_USER_EMAIL);
            assertThat(grouped).isEmpty();
        }
    }
}
