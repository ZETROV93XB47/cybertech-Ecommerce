package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.StockAlertPayload;
import com.novatech.cybertech.dto.response.stockalert.StockAlertResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Back-in-stock alerts: a user asks to be told when an out-of-stock product can be ordered again.
 * {@code STOCK_ALERT_JOB} finds the alerts whose product came back and sends them (one e-mail plus
 * one Web Push per browser); an alert is sent once, then it is over.
 */
public interface StockAlertService {

    /**
     * Creates the caller's alert for the product, or puts their previous (sent, cancelled or
     * expired) alert back to pending.
     *
     * @throws com.novatech.cybertech.exceptions.UserNotFoundException            unknown caller
     * @throws com.novatech.cybertech.exceptions.ProductNotFoundException         unknown product
     * @throws com.novatech.cybertech.exceptions.ProductAlreadyInStockException   the product can already be ordered
     * @throws com.novatech.cybertech.exceptions.StockAlertAlreadyExistsException an alert is already pending
     */
    StockAlertResponseDto subscribe(String keycloakId, UUID productUuid);

    /** Cancels the caller's pending alert for the product. Idempotent: no pending alert is a no-op. */
    void unsubscribe(String keycloakId, UUID productUuid);

    /** The caller's pending alerts. */
    Page<StockAlertResponseDto> findMine(String keycloakId, Pageable pageable);

    /**
     * Marks the alert as sent and returns the notifications to deliver (e-mail first, then one per
     * Web Push browser), in its own transaction: once this returns, a rerun of the job won't send
     * it again. Empty when the alert is no longer pending, the product is no longer available, or
     * the user is inactive.
     */
    List<NotificationContext<StockAlertPayload>> prepareAlert(Long alertId);

    /** Pending alerts older than {@code cybertech.stock-alert.expire-after-days} become expired. */
    int expireOldAlerts();
}
