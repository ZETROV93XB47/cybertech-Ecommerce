package com.novatech.cybertech.services.core;

import java.util.Map;
import java.util.UUID;

public interface StockService {
    void reserveStock(UUID orderUuid, Map<UUID, Integer> quantities);

    void commitStock(UUID orderUuid);

    void releaseStock(UUID orderUuid);

    /**
     * Atomically swaps an order's existing reservation for a new set of quantities: releases
     * every existing row and reserves fresh ones for {@code newQuantities}, deferring every
     * Redis write until all DB-level changes have succeeded. Used by {@code updateOrder}, which
     * must resize a reservation in place rather than call {@code reserveStock} directly — that
     * method's own idempotent short-circuit (an existing reservation just gets its TTL
     * refreshed) would not pick up the new item/quantity composition.
     *
     * @throws com.novatech.cybertech.exceptions.NotEnoughStockException when any new product
     *         no longer has enough available stock — the whole swap (including the release of
     *         the old reservation) rolls back with the transaction.
     */
    void resizeReservation(UUID orderUuid, Map<UUID, Integer> newQuantities);
}
