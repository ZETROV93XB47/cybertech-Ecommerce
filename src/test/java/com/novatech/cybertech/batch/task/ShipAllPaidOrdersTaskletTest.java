package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dispatcher.NotificationDispatcher;
import com.novatech.cybertech.dispatcher.ShippingDispatcher;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShipAllPaidOrdersTasklet}. Per progress.md (SA4.3R) the per-order
 * try/catch is already correctly implemented in production — we verify it green.
 */
@ExtendWith(MockitoExtension.class)
class ShipAllPaidOrdersTaskletTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShippingDispatcher shippingDispatcher;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private ShipAllPaidOrdersTasklet tasklet;

    private StepContribution stepContribution;
    private BaseTasklet.StepArguments stepArguments;

    @BeforeEach
    void setUp() {
        final JobInstance jobInstance = new JobInstance(1L, "SHIP_JOB");
        final JobExecution jobExecution = new JobExecution(1L, jobInstance, new JobParameters());
        final StepExecution stepExecution = new StepExecution("shipStep", jobExecution);
        stepContribution = new StepContribution(stepExecution);
        stepArguments = new BaseTasklet.StepArguments(
                "SHIP_JOB",
                LocalDateTime.now(),
                new JobParameters(),
                stepExecution
        );
    }

    private OrderEntity paidOrderForUser(final String email) {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().email(email).build();
        return OrderEntityBuilder.aValidOrderBuilder()
                .uuid(UUID.randomUUID())
                .status(OrderStatus.PAID)
                .userEntity(user)
                .build();
    }

    @Nested
    @DisplayName("Empty input")
    class EmptyInput {

        @Test
        @DisplayName("no PAID orders → ExitStatus.COMPLETED, no dispatchers invoked")
        void noPaidOrders_isNoOp() throws Exception {
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(Collections.emptyList());

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
            verifyNoInteractions(shippingDispatcher);
            verifyNoInteractions(notificationDispatcher);
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("ships every PAID order: dispatch, save with SHIPPED status, notify")
        void shipsEveryOrder() throws Exception {
            final OrderEntity o1 = paidOrderForUser("a@example.com");
            final OrderEntity o2 = paidOrderForUser("b@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1, o2));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            verify(shippingDispatcher, times(2)).dispatch(any(ShippingContext.class));
            verify(notificationDispatcher, times(2)).dispatch(any(NotificationContext.class));
            verify(orderRepository).save(o1);
            verify(orderRepository).save(o2);
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(o2.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        }

        @Test
        @DisplayName("the dispatched ShippingContext carries packageId = order.uuid.toString()")
        void shippingContextHasOrderUuidAsPackageId() throws Exception {
            final OrderEntity o1 = paidOrderForUser("a@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1));

            tasklet.execute(stepContribution, stepArguments);

            final ArgumentCaptor<ShippingContext> captor = ArgumentCaptor.forClass(ShippingContext.class);
            verify(shippingDispatcher).dispatch(captor.capture());
            assertThat(captor.getValue().getPackageId()).isEqualTo(o1.getUuid().toString());
        }

        @Test
        @DisplayName("the dispatched NotificationContext is SHIPPING_CONFIRMATION carrying a ShippingConfirmationPayload")
        void notificationContextIsShippingConfirmation() throws Exception {
            final OrderEntity o1 = paidOrderForUser("a@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1));

            tasklet.execute(stepContribution, stepArguments);

            final ArgumentCaptor<NotificationContext> captor = ArgumentCaptor.forClass(NotificationContext.class);
            verify(notificationDispatcher).dispatch(captor.capture());
            assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.SHIPPING_CONFIRMATION);
            assertThat(captor.getValue().getPayload()).isInstanceOf(ShippingConfirmationPayload.class);
            assertThat(((ShippingConfirmationPayload) captor.getValue().getPayload()).getOrderUuid()).isEqualTo(o1.getUuid());
        }
    }

    @Nested
    @DisplayName("Failure isolation — per-order try/catch")
    class FailureIsolation {

        @Test
        @DisplayName("ShippingDispatcher failure on N-th order does not abort remaining orders")
        void shippingFailureOnSecondOrder_doesNotAbortRemaining() throws Exception {
            final OrderEntity o1 = paidOrderForUser("a@example.com");
            final OrderEntity o2 = paidOrderForUser("b@example.com");
            final OrderEntity o3 = paidOrderForUser("c@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1, o2, o3));
            // Throw only when the dispatched ShippingContext is for o2
            org.mockito.Mockito.doAnswer(invocation -> {
                final ShippingContext ctx = invocation.getArgument(0);
                if (ctx.getPackageId().equals(o2.getUuid().toString())) {
                    throw new RuntimeException("shipping vendor down");
                }
                return null;
            }).when(shippingDispatcher).dispatch(any(ShippingContext.class));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(o2.getStatus()).isEqualTo(OrderStatus.PAID); // skipped
            assertThat(o3.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            verify(notificationDispatcher, times(2)).dispatch(any(NotificationContext.class));
            verify(orderRepository, times(2)).save(any(OrderEntity.class));
        }

        @Test
        @DisplayName("NotificationDispatcher failure is isolated to the failing order")
        void notificationFailure_isolated() throws Exception {
            final OrderEntity o1 = paidOrderForUser("a@example.com");
            final OrderEntity o2 = paidOrderForUser("b@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1, o2));
            doThrow(new RuntimeException("smtp down"))
                    .when(notificationDispatcher).dispatch(any(NotificationContext.class));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            // Per-order try/catch ensures FINISHED + ExitStatus.COMPLETED even if every notification fails
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
            // shippingDispatcher was attempted for both; orderRepository.save was attempted for both
            verify(shippingDispatcher, times(2)).dispatch(any(ShippingContext.class));
            verify(orderRepository, times(2)).save(any(OrderEntity.class));
        }

        @Test
        @DisplayName("when ShippingDispatcher fails the order is not saved and notification is not sent")
        void shippingFailure_skipsSaveAndNotify() throws Exception {
            final OrderEntity o1 = paidOrderForUser("a@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1));
            doThrow(new RuntimeException("shipping vendor down"))
                    .when(shippingDispatcher).dispatch(any(ShippingContext.class));

            tasklet.execute(stepContribution, stepArguments);

            verify(orderRepository, never()).save(any(OrderEntity.class));
            verifyNoInteractions(notificationDispatcher);
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.PAID);
        }
    }

}
