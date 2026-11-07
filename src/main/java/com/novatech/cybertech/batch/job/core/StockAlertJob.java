package com.novatech.cybertech.batch.job.core;

/**
 * Cron entry point of the back-in-stock alerts. See {@code StockAlertTasklet} for the detail.
 */
public interface StockAlertJob {

    /** Launches the stock alert job when {@code cybertech.stock-alert.job.activated} is true. */
    void startJob();
}
