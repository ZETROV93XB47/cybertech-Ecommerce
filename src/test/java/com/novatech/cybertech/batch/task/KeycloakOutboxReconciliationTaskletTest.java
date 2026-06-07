package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import com.novatech.cybertech.repositories.KeycloakOutboxRepository;
import com.novatech.cybertech.services.core.KeycloakOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link KeycloakOutboxReconciliationTasklet} — the crash-recovery scan
 * of the keycloak outbox. Pins: stale-PENDING selection delegated to the repository (status +
 * updatedAt threshold), per-row delegation to {@link KeycloakOutboxService#reconcile} with the
 * configured attempts cap, bounded page size, COMPLETED/FINISHED exit on both branches.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakOutboxReconciliationTaskletTest {

    @Mock KeycloakOutboxRepository outboxRepository;
    @Mock KeycloakOutboxService keycloakOutboxService;

    @InjectMocks KeycloakOutboxReconciliationTasklet tasklet;

    @BeforeEach
    void wireKnobs() {
        ReflectionTestUtils.setField(tasklet, "stalenessMinutes", 5L);
        ReflectionTestUtils.setField(tasklet, "batchSize", 50);
        ReflectionTestUtils.setField(tasklet, "maxAttempts", 5);
    }

    private BaseTasklet.StepArguments stepArguments() {
        return new BaseTasklet.StepArguments("job", LocalDateTime.now(), null, null);
    }

    @Test
    @DisplayName("every stale PENDING row is reconciled with the configured attempts cap")
    void reconcilesEveryStalePendingRow() throws Exception {
        final KeycloakOutboxEntity row = KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.DELETE).status(OutboxStatus.PENDING).keycloakId("kc").build();
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(row));
        final StepContribution contribution = mock(StepContribution.class);

        final RepeatStatus status = tasklet.execute(contribution, stepArguments());

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(keycloakOutboxService).reconcile(row, 5);
        verify(contribution).setExitStatus(ExitStatus.COMPLETED);
    }

    @Test
    @DisplayName("staleness window honored — threshold passed to the repository is now - stalenessMinutes")
    void thresholdReflectsStalenessWindow() throws Exception {
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        tasklet.execute(mock(StepContribution.class), stepArguments());

        final ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxRepository).findByStatusAndUpdatedAtBefore(eq(OutboxStatus.PENDING), threshold.capture(), any(Pageable.class));
        // Threshold must sit ~5 minutes in the past (small tolerance for test execution time).
        assertThat(threshold.getValue())
                .isBefore(LocalDateTime.now().minusMinutes(4))
                .isAfter(LocalDateTime.now().minusMinutes(6));
    }

    @Test
    @DisplayName("no stale rows — completes without touching the outbox service")
    void noStaleRows_completesQuietly() throws Exception {
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());
        final StepContribution contribution = mock(StepContribution.class);

        final RepeatStatus status = tasklet.execute(contribution, stepArguments());

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(keycloakOutboxService, never()).reconcile(any(), anyInt());
        verify(contribution).setExitStatus(ExitStatus.COMPLETED);
    }

    @Test
    @DisplayName("page size bounded by the batch-size knob (no unbounded sweep)")
    void pageSizeIsBounded() throws Exception {
        ReflectionTestUtils.setField(tasklet, "batchSize", 7);
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        tasklet.execute(mock(StepContribution.class), stepArguments());

        final ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(outboxRepository).findByStatusAndUpdatedAtBefore(eq(OutboxStatus.PENDING), any(LocalDateTime.class), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(7);
    }
}
