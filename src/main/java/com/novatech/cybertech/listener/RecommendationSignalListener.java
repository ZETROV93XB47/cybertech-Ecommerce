package com.novatech.cybertech.listener;

import com.novatech.cybertech.events.RecommendationSignalsEvent;
import com.novatech.cybertech.services.core.UserEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;

/**
 * Writes the recommendation signals of a business action (purchase, cart, wishlist, review) to
 * MongoDB, where {@code GorseSyncTasklet} picks them up.
 *
 * <p>WHY {@link TransactionPhase#AFTER_COMMIT}: a rolled-back action (refused add, payment
 * transition not applied, moderation refusal) must count for nothing. WHY {@link Async}: a slow or
 * unreachable MongoDB must not delay the cart, review or payment-confirmation response;
 * {@link UserEventService#recordServerSignals} logs the failure and never throws.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationSignalListener {

    private final UserEventService userEventService;

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final RecommendationSignalsEvent event) {
        log.debug("Recording {} recommendation signal(s)", event.getSignals().size());
        userEventService.recordServerSignals(event.getSignals());
    }
}
