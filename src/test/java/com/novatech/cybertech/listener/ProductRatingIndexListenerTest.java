package com.novatech.cybertech.listener;

import com.novatech.cybertech.events.ProductReviewsChangedEvent;
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
class ProductRatingIndexListenerTest {

    @Mock private ProductSearchIndexService productSearchIndexService;

    @InjectMocks
    private ProductRatingIndexListener listener;

    @Test
    @DisplayName("refreshes the ratings of the event's product")
    void refreshesRatings() {
        final UUID productUuid = UUID.randomUUID();

        listener.onProductReviewsChanged(new ProductReviewsChangedEvent(this, productUuid));

        verify(productSearchIndexService).refreshRatings(productUuid);
    }

    @Test
    @DisplayName("an Elasticsearch failure is logged, never rethrown")
    void swallowsIndexFailure() {
        final UUID productUuid = UUID.randomUUID();
        doThrow(new IllegalStateException("ES down")).when(productSearchIndexService).refreshRatings(productUuid);

        assertThatCode(() -> listener.onProductReviewsChanged(new ProductReviewsChangedEvent(this, productUuid)))
                .doesNotThrowAnyException();
    }
}
