package com.novatech.cybertech.batch.job.core;

/**
 * Cron entry point that pushes products, users, and recent behavioral feedback to Gorse so its
 * offline recommenders have data to train on. See {@code GorseSyncTasklet} for the per-phase
 * detail.
 */
public interface GorseSyncJob {

    /** Launches the Gorse sync batch job when {@code cybertech.gorse.sync.job.activated} is true. */
    void startJob();
}
