package com.novatech.cybertech.batch.job.core;

/**
 * Cron entry point that releases stock reserved by carts/orders whose reservation window expired
 * without a completed checkout.
 */
public interface StockCleanupJob {

    /** Launches the cleanup batch job when {@code cybertech.stock.cleanup.job.activated} is true. */
    void startJob();
}
