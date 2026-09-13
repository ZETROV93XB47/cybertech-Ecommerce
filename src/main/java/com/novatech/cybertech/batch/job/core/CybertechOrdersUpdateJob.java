package com.novatech.cybertech.batch.job.core;

import org.springframework.batch.core.job.JobExecution;

/**
 * Cron entry point for the failed-payment / cancelled-orders reporting pipeline.
 *
 * <p>Chains three steps in one run: flag orders with a failed payment, cancel pending orders past
 * their time limit, then ship every order left {@code AWAITING_SHIPPING} — followed by a daily
 * summary emitted by {@code OrdersSummaryReportListener}.
 */
public interface CybertechOrdersUpdateJob {

    /**
     * Launches the pipeline when {@code cybertech.orders.update.job.activated} is true.
     *
     * @return the resulting {@link JobExecution}, or {@code null} if disabled or the launcher rejected the run.
     */
    JobExecution startJob();
}
