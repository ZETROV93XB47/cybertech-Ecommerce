package com.novatech.cybertech.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Fired by {@link com.novatech.cybertech.services.implementation.ReviewManagementServiceImp} when a
 * review of a product is created, modified or deleted. Consumed AFTER_COMMIT by
 * {@link com.novatech.cybertech.listener.ProductRatingIndexListener}, which re-indexes the product's
 * {@code averageRating} / {@code reviewCount} (the "best rated" sort of the catalog search).
 */
@Getter
public class ProductReviewsChangedEvent extends ApplicationEvent {

    private final UUID productUuid;

    public ProductReviewsChangedEvent(final Object source, final UUID productUuid) {
        super(source);
        this.productUuid = productUuid;
    }
}
