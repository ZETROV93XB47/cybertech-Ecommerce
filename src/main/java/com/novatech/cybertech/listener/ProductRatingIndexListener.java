package com.novatech.cybertech.listener;

import com.novatech.cybertech.events.ProductReviewsChangedEvent;
import com.novatech.cybertech.services.core.ProductSearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_ASYNC_TASK_EXECUTOR;

/**
 * Re-indexes a product's rating statistics once a change to its reviews has committed.
 *
 * <p>WHY {@link TransactionPhase#AFTER_COMMIT}: the statistics are read back from MySQL, so the
 * review change must be durable first. An Elasticsearch failure is logged and never reaches the
 * review request; the next change of the product's reviews, or a reindex, repairs the values.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductRatingIndexListener {

    private final ProductSearchIndexService productSearchIndexService;

    @Async(APPLICATION_ASYNC_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductReviewsChanged(final ProductReviewsChangedEvent event) {
        try {
            productSearchIndexService.refreshRatings(event.getProductUuid());
        } catch (RuntimeException e) {
            log.warn("Could not re-index the ratings of product {}", event.getProductUuid(), e);
        }
    }
}
