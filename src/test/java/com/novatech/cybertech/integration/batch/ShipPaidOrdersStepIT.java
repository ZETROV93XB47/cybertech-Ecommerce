package com.novatech.cybertech.integration.batch;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real {@code ShipAllAwaitingShippingOrdersTasklet} step through Spring Batch, so the
 * tasklet executes inside the step's chunk transaction exactly as in production.
 *
 * <p>Regression pin: {@code ShipOrderTransactionalDelegate.claimAndShip} used to mutate the order
 * instance managed by that outer session. Its own {@code REQUIRES_NEW} commit bumped the
 * {@code @Version}, then the step's commit flushed the stale outer copy
 * ({@code UPDATE ... WHERE version=?} → 0 rows) and the step failed with
 * {@code ObjectOptimisticLockingFailureException}. A mock-based unit test cannot see this — it
 * needs a real transaction manager and a real database.</p>
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ShipPaidOrdersStepIT — ship step inside a real Spring Batch transaction")
class ShipPaidOrdersStepIT {

    private static final String SHIP_STEP = "ShipAllAwaitingShippingOrdersTasklet";

    @Autowired private JobRepository jobRepository;
    @Autowired private JobLauncher jobLauncher;
    @Autowired @Qualifier(SHIP_STEP) private Step shipStep;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TestDataCleaner testDataCleaner;

    @BeforeEach
    void wipe() {
        testDataCleaner.wipe();
    }

    @Test
    @DisplayName("PAID order → step COMPLETED and order persisted SHIPPED with shippedAt")
    void paidOrderIsShippedAndStepCompletes() throws Exception {
        final UUID orderUuid = seedOrder(OrderStatus.PAID, null);

        final JobExecution execution = runShipStep();

        assertStepCompleted(execution);
        final OrderEntity reloaded = orderRepository.findByUuid(orderUuid).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(reloaded.getShippedAt()).isNotNull();
    }

    @Test
    @DisplayName("several PAID orders in one run → all SHIPPED, no stale-version flush at the step commit")
    void severalPaidOrdersAreAllShippedInOneRun() throws Exception {
        final UUID first = seedOrder(OrderStatus.PAID, null);
        final UUID second = seedOrder(OrderStatus.PAID, null);
        final UUID third = seedOrder(OrderStatus.PAID, null);

        final JobExecution execution = runShipStep();

        assertStepCompleted(execution);
        assertThat(orderRepository.findByUuid(first).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(orderRepository.findByUuid(second).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(orderRepository.findByUuid(third).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("PAID order already shipped once (re-promoted by an update) → left PAID, step COMPLETED")
    void rePromotedOrderIsNotShippedAgain() throws Exception {
        final LocalDateTime firstShipment = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);
        final UUID orderUuid = seedOrder(OrderStatus.PAID, firstShipment);

        final JobExecution execution = runShipStep();

        assertStepCompleted(execution);
        final OrderEntity reloaded = orderRepository.findByUuid(orderUuid).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.getShippedAt()).isEqualTo(firstShipment);
    }

    private UUID seedOrder(final OrderStatus status, final LocalDateTime shippedAt) {
        final UserEntity user = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .email("ship-it+" + UUID.randomUUID() + "@example.com")
                .keycloakId("kc-" + UUID.randomUUID())
                .build());
        final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                .userEntity(user)
                .status(status)
                .shippedAt(shippedAt)
                .build();
        return orderRepository.save(order).getUuid();
    }

    private JobExecution runShipStep() throws Exception {
        final Job job = new JobBuilder("SHIP_PAID_ORDERS_STEP_IT", jobRepository)
                .start(shipStep)
                .build();
        return jobLauncher.run(job, new JobParametersBuilder()
                .addString("run", UUID.randomUUID().toString())
                .toJobParameters());
    }

    private static void assertStepCompleted(final JobExecution execution) {
        assertThat(execution.getStepExecutions())
                .singleElement()
                .satisfies(step -> assertThat(step.getStatus())
                        .as("step failures: %s", step.getFailureExceptions())
                        .isEqualTo(BatchStatus.COMPLETED));
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
}
