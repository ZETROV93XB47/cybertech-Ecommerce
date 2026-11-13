package com.novatech.cybertech.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload of the daily suggestion e-mail ({@code RECOMMENDATION_DIGEST}). Everything the template
 * shows is resolved when the e-mail is prepared (names, prices, links), so a redelivery by
 * {@code REDELIVER_FAILED_NOTIFICATIONS_JOB} sends the same e-mail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationDigestPayload implements NotificationPayload {

    private UserContactDto userContactDto;
    private List<RecommendedProductDto> products;
    private String unsubscribeUrl;
}
