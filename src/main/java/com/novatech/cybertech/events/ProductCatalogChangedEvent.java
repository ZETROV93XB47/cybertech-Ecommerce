package com.novatech.cybertech.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Fired by {@link com.novatech.cybertech.services.implementation.ProductManagementServiceImp} when a
 * product is created, modified or deleted. Consumed AFTER_COMMIT by
 * {@link com.novatech.cybertech.listener.ProductSearchIndexListener}, which aligns the product's
 * search document with what MySQL durably holds (indexed if the product exists, removed otherwise).
 */
@Getter
public class ProductCatalogChangedEvent extends ApplicationEvent {

    private final UUID productUuid;

    public ProductCatalogChangedEvent(final Object source, final UUID productUuid) {
        super(source);
        this.productUuid = productUuid;
    }
}
