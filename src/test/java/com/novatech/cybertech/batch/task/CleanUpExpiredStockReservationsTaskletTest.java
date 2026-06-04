package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.entities.enums.ReservationStatus;
import com.novatech.cybertech.fixtures.builders.StockEntityBuilder;
import com.novatech.cybertech.repositories.StockRepository;
import com.novatech.cybertech.services.core.StockService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CleanUpExpiredStockReservationsTasklet}.
 *
 * Per progress.md (SA4.3R / F2): BUG-110 fix lands as a switch from
 * {@code stockRepository.findAll()} to
 * {@code stockRepository.findByReservationStatusAndCreatedAtBefore(ReservationStatus.ACTIVE, threshold)}.
 * We verify it green here.
 */
@ExtendWith(MockitoExtension.class)
class CleanUpExpiredStockReservationsTaskletTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockService stockService;

    @InjectMocks
    private CleanUpExpiredStockReservationsTasklet tasklet;

    private StepContribution stepContribution;
    private BaseTasklet.StepArguments stepArguments;

    @BeforeEach
    void setUp() {
        final JobInstance jobInstance = new JobInstance(1L, "STOCK_CLEANUP");
        final JobExecution jobExecution = new JobExecution(1L, jobInstance, new JobParameters());
        final StepExecution stepExecution = new StepExecution("cleanupStep", jobExecution);
        stepContribution = new StepContribution(stepExecution);
        stepArguments = new BaseTasklet.StepArguments(
                "STOCK_CLEANUP",
                LocalDateTime.now(),
                new JobParameters(),
                stepExecution
        );
    }

    private StockEntity stockFor(final UUID orderUuid) {
        return StockEntityBuilder.aValidStockBuilder().orderUuid(orderUuid).build();
    }

    @Nested
    @DisplayName("Repository query — BUG-110 fix verification")
    class RepoQuery {

        @Test
        @DisplayName("BUG-110 (FIXED in F2): uses findByReservationStatusAndCreatedAtBefore(ACTIVE, threshold), not findAll()")
        void usesNarrowQueryNotFindAll() throws Exception {
            when(stockRepository.findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            tasklet.execute(stepContribution, stepArguments);

            verify(stockRepository).findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class));
            verify(stockRepository, never()).findAll();
        }

        @Test
        @DisplayName("threshold passed to repo is roughly now - 7 minutes")
        void thresholdIsNowMinus7Minutes() throws Exception {
            when(stockRepository.findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());
            final LocalDateTime before = LocalDateTime.now();

            tasklet.execute(stepContribution, stepArguments);

            final ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(stockRepository).findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), captor.capture());
            final LocalDateTime threshold = captor.getValue();
            // Reasonable bracket — should be ~7min in the past
            assertThat(threshold).isBetween(before.minusMinutes(8), LocalDateTime.now().minusMinutes(6));
        }
    }

    @Nested
    @DisplayName("Empty input")
    class EmptyInput {

        @Test
        @DisplayName("no expired reservations → ExitStatus.COMPLETED, no service calls")
        void noExpired_isNoOp() throws Exception {
            when(stockRepository.findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
            verifyNoInteractions(stockService);
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("releases stock for every distinct expired orderUuid")
        void releasesStockForEveryDistinctOrder() throws Exception {
            final UUID o1 = UUID.randomUUID();
            final UUID o2 = UUID.randomUUID();
            when(stockRepository.findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class)))
                    .thenReturn(List.of(stockFor(o1), stockFor(o2)));

            tasklet.execute(stepContribution, stepArguments);

            verify(stockService).releaseStock(o1);
            verify(stockService).releaseStock(o2);
        }

        @Test
        @DisplayName("deduplicates orderUuid → releaseStock called once per distinct order")
        void deduplicatesByOrderUuid() throws Exception {
            final UUID orderUuid = UUID.randomUUID();
            when(stockRepository.findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class)))
                    .thenReturn(List.of(stockFor(orderUuid), stockFor(orderUuid), stockFor(orderUuid)));

            tasklet.execute(stepContribution, stepArguments);

            verify(stockService, times(1)).releaseStock(orderUuid);
        }

        @Test
        @DisplayName("sets ExitStatus.COMPLETED after a successful run")
        void setsCompletedExitStatus() throws Exception {
            when(stockRepository.findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class)))
                    .thenReturn(List.of(stockFor(UUID.randomUUID())));

            tasklet.execute(stepContribution, stepArguments);

            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        }

        @Test
        @DisplayName("returns FINISHED RepeatStatus after a successful run")
        void returnsFinished() throws Exception {
            when(stockRepository.findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class)))
                    .thenReturn(List.of(stockFor(UUID.randomUUID())));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        }
    }

    @Nested
    @DisplayName("Failure paths")
    class FailurePaths {

        /**
         * Per-reservation try/catch is NOT implemented in {@link CleanUpExpiredStockReservationsTasklet}
         * (compare with {@link CancelAllPendingOrdersByTimeTasklet} which got the BUG-111 patch).
         * The current behaviour is: a single {@code releaseStock} failure aborts remaining cleanups
         * by propagating to {@link BaseTasklet}'s ChunkContext wrapper which swallows the exception.
         * This test PINS that current behaviour (typed execute() throws).
         */
        @Test
        @DisplayName("typed execute() lets stockService failure propagate (no per-item try/catch)")
        void releaseStockThrows_propagatesFromTypedExecute() throws Exception {
            final UUID o1 = UUID.randomUUID();
            final UUID o2 = UUID.randomUUID();
            when(stockRepository.findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class)))
                    .thenReturn(List.of(stockFor(o1), stockFor(o2)));
            doThrow(new RuntimeException("redis lock failed")).when(stockService).releaseStock(any(UUID.class));

            assertThatThrownBy(() -> tasklet.execute(stepContribution, stepArguments))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("redis lock failed");
        }

        @Test
        @DisplayName("repository failure is propagated from typed execute()")
        void repositoryThrows_propagates() {
            when(stockRepository.findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class)))
                    .thenThrow(new RuntimeException("DB down"));

            assertThatThrownBy(() -> tasklet.execute(stepContribution, stepArguments))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB down");
        }
    }

    @Nested
    @DisplayName("BUG-110 fix verification — narrow query in use")
    class Bug110FixVerification {

        @Test
        @DisplayName("BUG-110 FIX (F2): tasklet calls findByReservationStatusAndCreatedAtBefore and never findAll")
        void bug110_narrowQueryUsed_findAllNeverCalled() throws Exception {
            // BUG-110 FIX: original behaviour loaded every reservation via stockRepository.findAll()
            // and filtered in memory. F2 switched to the narrow server-side query. We pin the fix
            // here as a green assertion (companion to RepoQuery#usesNarrowQueryNotFindAll).
            when(stockRepository.findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            tasklet.execute(stepContribution, stepArguments);

            verify(stockRepository).findByReservationStatusAndCreatedAtBefore(eq(ReservationStatus.ACTIVE), any(LocalDateTime.class));
            verify(stockRepository, never()).findAll();
        }
    }
}
