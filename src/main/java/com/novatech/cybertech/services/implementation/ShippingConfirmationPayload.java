package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.NotificationPayload;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * <p><b>Phase 3 enabler:</b> {@code @NoArgsConstructor} + {@code @AllArgsConstructor}
 * are required so Jackson 3 can deserialize this payload through the
 * polymorphic {@link NotificationPayload} discriminator when the batch redrive
 * tasklet rebuilds a {@link com.novatech.cybertech.dto.data.NotificationContext}
 * from a persisted {@link com.novatech.cybertech.dto.data.NotificationRedrivePayload}
 * snapshot. {@code @Data} + {@code @Builder} alone generates an all-args
 * constructor that suppresses the default no-args one Jackson needs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingConfirmationPayload implements NotificationPayload {
    private UUID orderUuid;
    private String userName;
    private ShippingType shippingType;
    private ShippingProvider shippingProvider;
}
