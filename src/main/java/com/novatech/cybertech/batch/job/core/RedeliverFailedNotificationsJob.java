package com.novatech.cybertech.batch.job.core;

/**
 * Cron entry point for the notification redelivery long-tail safety net.
 *
 * <p>In-process retries already happen first via Resilience4j inside
 * {@code NotificationRetryableDeliveryImp} (3 attempts, exponential backoff, all within a few
 * seconds). This job is the cumulative backstop that later drains rows left in
 * {@code NotificationStatus#PENDING_RETRY} after that in-process budget was spent — typical cause
 * is an outage longer than the in-process backoff window (mail server down, DNS hiccup, etc.).
 */
public interface RedeliverFailedNotificationsJob {

    /** Launches the redelivery batch job when {@code cybertech.notification.redelivery.job.activated} is true. */
    void startJob();
}
