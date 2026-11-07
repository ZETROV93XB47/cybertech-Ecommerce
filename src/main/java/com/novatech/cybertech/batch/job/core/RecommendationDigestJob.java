package com.novatech.cybertech.batch.job.core;

/**
 * Cron entry point of the daily product-suggestion e-mail. See
 * {@code RecommendationDigestTasklet} for the detail.
 */
public interface RecommendationDigestJob {

    /** Launches the suggestion e-mail job when {@code cybertech.recommendation.digest.job.activated} is true. */
    void startJob();
}
