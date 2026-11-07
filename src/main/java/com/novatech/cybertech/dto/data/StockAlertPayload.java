package com.novatech.cybertech.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload of the back-in-stock alert ({@code STOCK_ALERT}), e-mail and Web Push. Resolved when
 * the alert is prepared, so a redelivery sends the same message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertPayload implements NotificationPayload {

    private UserContactDto userContactDto;
    private UUID productUuid;
    private String productName;
    private BigDecimal price;
    /** Null when the product has no photo. */
    private String imageUrl;
    private String productUrl;
}
