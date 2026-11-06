package com.novatech.cybertech.listener;

import com.novatech.cybertech.events.ProductCatalogChangedEvent;
import com.novatech.cybertech.services.core.ProductSearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Updates the product search index once a product creation, modification or deletion has committed.
 *
 * <p>WHY {@link TransactionPhase#AFTER_COMMIT}: writing to Elasticsearch inside the MySQL transaction
 * left a search document for a product whose transaction then rolled back (or removed the document
 * of a product whose deletion rolled back). The document is now rebuilt from the committed row.
 *
 * <p>WHY synchronous (no {@code @Async}): the admin who just saved a product expects to find it in
 * the catalog search as soon as the request returns. An Elasticsearch failure is logged and never
 * reaches the request (the product is saved); the next change of the product repairs the document.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchIndexListener {

    private final ProductSearchIndexService productSearchIndexService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductCatalogChanged(final ProductCatalogChangedEvent event) {
        try {
            productSearchIndexService.syncFromDatabase(event.getProductUuid());
        } catch (RuntimeException e) {
            log.error("Product {} saved in the database but its search document could not be updated", event.getProductUuid(), e);
        }
    }
}
