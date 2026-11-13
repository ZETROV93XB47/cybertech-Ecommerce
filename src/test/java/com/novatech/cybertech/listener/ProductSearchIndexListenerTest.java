package com.novatech.cybertech.listener;

import com.novatech.cybertech.events.ProductCatalogChangedEvent;
import com.novatech.cybertech.services.core.ProductSearchIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductSearchIndexListenerTest {

    @Mock private ProductSearchIndexService productSearchIndexService;

    @InjectMocks
    private ProductSearchIndexListener listener;

    @Test
    @DisplayName("aligns the event's product document with the database")
    void syncsTheProduct() {
        final UUID productUuid = UUID.randomUUID();

        listener.onProductCatalogChanged(new ProductCatalogChangedEvent(this, productUuid));

        verify(productSearchIndexService).syncFromDatabase(productUuid);
    }

    @Test
    @DisplayName("an Elasticsearch failure is logged, never rethrown (the product is already saved)")
    void swallowsIndexFailure() {
        final UUID productUuid = UUID.randomUUID();
        doThrow(new IllegalStateException("ES down")).when(productSearchIndexService).syncFromDatabase(productUuid);

        assertThatCode(() -> listener.onProductCatalogChanged(new ProductCatalogChangedEvent(this, productUuid)))
                .doesNotThrowAnyException();
    }
}
