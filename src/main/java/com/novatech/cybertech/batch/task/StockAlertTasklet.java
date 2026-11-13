package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.StockAlertPayload;
import com.novatech.cybertech.repositories.StockAlertRepository;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import com.novatech.cybertech.services.core.StockAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends the back-in-stock alerts whose product can be ordered again.
 *
 * <p>Detection by polling rather than by an event on each stock change: stock comes back through
 * the admin update, order cancellation, payment failure, refund, reservation expiry and order
 * resize. Reading the current stock every few minutes covers all of them (and any future one)
 * without touching {@code StockServiceImp}, for at most one cron interval of delay.
 *
 * <p>First expires the alerts older than {@code cybertech.stock-alert.expire-after-days}, then
 * reads the pending alerts with an available product by pages of ids. Per alert:
 * {@link StockAlertService#prepareAlert} marks it sent in its own transaction, then each
 * notification goes through {@link NotificationRetryableDelivery}. A failure for one alert is
 * logged and the job moves on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAlertTasklet extends BaseTasklet {

    private static final long FIRST_ID = 0L;

    private final StockAlertRepository stockAlertRepository;
    private final StockAlertService stockAlertService;
    private final NotificationRetryableDelivery retryableDelivery;

    @Value("${cybertech.stock-alert.batch-size:100}")
    private int batchSize;

    @Override
    public RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) {
        log.info("Starting StockAlertTasklet");
        stockAlertService.expireOldAlerts();

        int sent = 0;
        int failed = 0;
        List<Long> page = nextPage(FIRST_ID);
        while (!page.isEmpty()) {
            for (final Long alertId : page) {
                try {
                    if (send(alertId)) {
                        sent++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Back-in-stock alert {} failed, moving on to the next alert", alertId, e);
                }
            }
            page = nextPage(page.getLast());
        }
        stepContribution.setExitStatus(ExitStatus.COMPLETED);
        log.info("StockAlertTasklet finished: {} alert(s) sent, {} failure(s)", sent, failed);
        return RepeatStatus.FINISHED;
    }

    private List<Long> nextPage(final long afterId) {
        return stockAlertRepository.findPendingIdsWithAvailableProductAfter(afterId, PageRequest.of(0, batchSize));
    }

    private boolean send(final Long alertId) {
        final List<NotificationContext<StockAlertPayload>> notifications = stockAlertService.prepareAlert(alertId);
        notifications.forEach(retryableDelivery::deliver);
        return !notifications.isEmpty();
    }
}
