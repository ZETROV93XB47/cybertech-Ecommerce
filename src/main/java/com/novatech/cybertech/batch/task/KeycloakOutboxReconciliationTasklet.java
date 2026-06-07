package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import com.novatech.cybertech.repositories.KeycloakOutboxRepository;
import com.novatech.cybertech.services.core.KeycloakOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Crash-recovery for the Keycloak outbox (user saga CREATE/UPDATE/DELETE). Scans rows left
 * {@link OutboxStatus#PENDING} beyond a staleness window — a healthy synchronous request flips
 * its row terminal within milliseconds, so anything PENDING past the window can only be a crash
 * leftover — and reconciles each idempotently via {@link KeycloakOutboxService#reconcile}.
 *
 * <p>Mirrors {@link RedeliverFailedNotificationsTasklet} structurally: one-shot scan, bounded
 * page (no unbounded sweep), per-row error isolation inside {@code reconcile}, COMPLETED +
 * FINISHED exit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakOutboxReconciliationTasklet extends BaseTasklet {

    private final KeycloakOutboxRepository outboxRepository;
    private final KeycloakOutboxService keycloakOutboxService;

    @Value("${cybertech.keycloak.outbox.staleness-minutes:5}")
    private long stalenessMinutes;

    @Value("${cybertech.keycloak.outbox.batch-size:50}")
    private int batchSize;

    @Value("${cybertech.keycloak.outbox.max-attempts:5}")
    private int maxAttempts;

    @Override
    public RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) {
        final LocalDateTime threshold = LocalDateTime.now().minusMinutes(stalenessMinutes);
        final List<KeycloakOutboxEntity> stale = outboxRepository.findByStatusAndUpdatedAtBefore(
                OutboxStatus.PENDING, threshold, PageRequest.ofSize(batchSize));

        if (stale.isEmpty()) {
            log.info("No stale PENDING keycloak-outbox rows to reconcile");
            stepContribution.setExitStatus(ExitStatus.COMPLETED);
            return RepeatStatus.FINISHED;
        }

        log.info("Reconciling {} stale keycloak-outbox rows", stale.size());
        stale.forEach(row -> keycloakOutboxService.reconcile(row, maxAttempts));

        stepContribution.setExitStatus(ExitStatus.COMPLETED);
        return RepeatStatus.FINISHED;
    }
}
