package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.entities.enums.ReservationStatus;

import java.util.UUID;

/**
 * Test fixture builder for {@link StockEntity}. Presets {@code uuid} explicitly because builders
 * bypass {@code BaseEntity#prePersist}.
 */
public final class StockEntityBuilder {

    private StockEntityBuilder() {
    }

    public static StockEntity aValidStock() {
        return aValidStockBuilder().build();
    }

    public static StockEntity.StockEntityBuilder<?, ?> aValidStockBuilder() {
        return StockEntity.builder()
                .uuid(UUID.randomUUID())
                .orderUuid(UUID.randomUUID())
                .productUuid(UUID.randomUUID())
                .quantity(1)
                .reservationStatus(ReservationStatus.ACTIVE);
    }
}
