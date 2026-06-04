package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dispatcher.NotificationDispatcher;
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
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShipAllPaidOrdersTasklet}.
 *
 * <p>Wave 3 regression-fix: the per-order claim+dispatch now runs through
 * {@link ShipOrderTransactionalDelegate#claimAndShip(OrderEntity, ShippingContext)}
 * (REQUIRES_NEW). The tasklet itself is no longer {@code @Transactional}, so the
 * optimistic-lock flush commits inside the delegate call and the per-order
 * try/catch on {@link OptimisticLockingFailureException} actually fires. The mocks
 * for the delegate replicate that contract: when the real delegate would set
 * {@code AWAITING_SHIPPING} + save and then dispatch, the test's
 * {@code doAnswer(...)} on {@code claimAndShip} mutates the order's status and
 * delegates dispatch back to the {@link com.novatech.cybertech.dispatcher.ShippingDispatcher}
 * mock so existing failure-isolation assertions still cover the dispatch path.</p>
 */
@ExtendWith(MockitoExtension.class)
class ShipAllPaidOrdersTaskletTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private com.novatech.cybertech.dispatcher.ShippingDispatcher shippingDispatcher;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Mock
    private ShipOrderTransactionalDelegate shipOrderDelegate;

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

    /**
     * Default delegate behavior: simulate the production REQUIRES_NEW delegate by mutating
     * the in-memory order status, persisting the claim via the orderRepository mock and
     * forwarding dispatch to the shippingDispatcher mock — keeping the existing failure
     * isolation assertions valid against the new wiring.
     */
    private void wireDelegateToSimulateClaimAndShip() {
        doAnswer(invocation -> {
            final OrderEntity ord = invocation.getArgument(0);
            final ShippingContext ctx = invocation.getArgument(1);
            // Mirror the real REQUIRES_NEW delegate contract: claim (PAID -> AWAITING_SHIPPING) +
            // save, dispatch, then mark SHIPPED + save — all inside the delegate now (the tasklet
            // no longer performs the SHIPPED transition).
            ord.setStatus(OrderStatus.AWAITING_SHIPPING);
            orderRepository.save(ord);
            shippingDispatcher.dispatch(ctx);
            ord.setStatus(OrderStatus.SHIPPED);
            orderRepository.save(ord);
            return null;
        }).when(shipOrderDelegate).claimAndShip(any(OrderEntity.class), any(ShippingContext.class));
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
            verifyNoInteractions(shipOrderDelegate);
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("ships every PAID order: delegate.claimAndShip then save SHIPPED, notify")
        void shipsEveryOrder() throws Exception {
            wireDelegateToSimulateClaimAndShip();
            final OrderEntity o1 = paidOrderForUser("a@example.com");
            final OrderEntity o2 = paidOrderForUser("b@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1, o2));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            verify(shipOrderDelegate, times(2)).claimAndShip(any(OrderEntity.class), any(ShippingContext.class));
            verify(shippingDispatcher, times(2)).dispatch(any(ShippingContext.class));
            verify(notificationDispatcher, times(2)).dispatch(any(NotificationContext.class));
            // Each order: claim save (inside delegate) + SHIPPED save (after dispatch).
            verify(orderRepository, times(2)).save(o1);
            verify(orderRepository, times(2)).save(o2);
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(o2.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        }

        @Test
        @DisplayName("the dispatched ShippingContext carries packageId = order.uuid.toString()")
        void shippingContextHasOrderUuidAsPackageId() throws Exception {
            wireDelegateToSimulateClaimAndShip();
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
            wireDelegateToSimulateClaimAndShip();
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
            doAnswer(invocation -> {
                final OrderEntity ord = invocation.getArgument(0);
                final ShippingContext ctx = invocation.getArgument(1);
                ord.setStatus(OrderStatus.AWAITING_SHIPPING);
                orderRepository.save(ord);
                if (ctx.getPackageId().equals(o2.getUuid().toString())) {
                    throw new RuntimeException("shipping vendor down");
                }
                shippingDispatcher.dispatch(ctx);
                ord.setStatus(OrderStatus.SHIPPED);
                orderRepository.save(ord);
                return null;
            }).when(shipOrderDelegate).claimAndShip(any(OrderEntity.class), any(ShippingContext.class));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            // o2 was claimed (PAID -> AWAITING_SHIPPING + save) before dispatch failed,
            // so its in-memory status remains AWAITING_SHIPPING after the catch.
            assertThat(o2.getStatus()).isEqualTo(OrderStatus.AWAITING_SHIPPING);
            assertThat(o3.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            verify(notificationDispatcher, times(2)).dispatch(any(NotificationContext.class));
            // 3 claim saves + 2 SHIPPED saves = 5 total.
            verify(orderRepository, times(5)).save(any(OrderEntity.class));
        }

        @Test
        @DisplayName("NotificationDispatcher failure is isolated to the failing order")
        void notificationFailure_isolated() throws Exception {
            wireDelegateToSimulateClaimAndShip();
            final OrderEntity o1 = paidOrderForUser("a@example.com");
            final OrderEntity o2 = paidOrderForUser("b@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1, o2));
            doThrow(new RuntimeException("smtp down"))
                    .when(notificationDispatcher).dispatch(any(NotificationContext.class));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            // Per-order try/catch ensures FINISHED + ExitStatus.COMPLETED even if every notification fails
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
            // shippingDispatcher was attempted for both; each order is saved twice
            // (AWAITING_SHIPPING claim inside the delegate + SHIPPED) before the notification fails = 4.
            verify(shippingDispatcher, times(2)).dispatch(any(ShippingContext.class));
            verify(orderRepository, times(4)).save(any(OrderEntity.class));
        }

        @Test
        @DisplayName("when ShippingDispatcher fails the order's claim survives and notification is not sent")
        void shippingFailure_skipsSaveAndNotify() throws Exception {
            final OrderEntity o1 = paidOrderForUser("a@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1));
            doAnswer(invocation -> {
                final OrderEntity ord = invocation.getArgument(0);
                ord.setStatus(OrderStatus.AWAITING_SHIPPING);
                orderRepository.save(ord);
                throw new RuntimeException("shipping vendor down");
            }).when(shipOrderDelegate).claimAndShip(any(OrderEntity.class), any(ShippingContext.class));

            tasklet.execute(stepContribution, stepArguments);

            // Atomic-claim fix: the AWAITING_SHIPPING claim save happens BEFORE dispatch (inside
            // the REQUIRES_NEW delegate), so a dispatch failure leaves the order claimed
            // (saved once) but never SHIPPED.
            verify(orderRepository, times(1)).save(any(OrderEntity.class));
            verifyNoInteractions(notificationDispatcher);
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.AWAITING_SHIPPING);
        }

        @Test
        @DisplayName("OptimisticLockingFailureException from delegate skips the order without aborting the batch")
        void optimisticLockFailure_skipsOrderWithoutAbortingBatch() throws Exception {
            final OrderEntity o1 = paidOrderForUser("a@example.com");
            final OrderEntity o2 = paidOrderForUser("b@example.com");
            when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1, o2));

            // o1 loses the race against ShippingListener → delegate raises an
            // OptimisticLockingFailureException at its REQUIRES_NEW commit boundary.
            // o2 is processed normally to prove the per-order isolation.
            doAnswer(invocation -> {
                final OrderEntity ord = invocation.getArgument(0);
                final ShippingContext ctx = invocation.getArgument(1);
                if (ord.getUuid().equals(o1.getUuid())) {
                    throw new OptimisticLockingFailureException("Race lost on order " + ord.getUuid());
                }
                ord.setStatus(OrderStatus.AWAITING_SHIPPING);
                orderRepository.save(ord);
                shippingDispatcher.dispatch(ctx);
                ord.setStatus(OrderStatus.SHIPPED);
                orderRepository.save(ord);
                return null;
            }).when(shipOrderDelegate).claimAndShip(any(OrderEntity.class), any(ShippingContext.class));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
            // o1 was skipped silently (status untouched in-memory after the delegate threw).
            assertThat(o1.getStatus()).isEqualTo(OrderStatus.PAID);
            // o2 was processed end-to-end.
            assertThat(o2.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            verify(notificationDispatcher, times(1)).dispatch(any(NotificationContext.class));
        }
    }

}
