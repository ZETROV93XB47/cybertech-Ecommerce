package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.RecommendationDigestPayload;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.services.core.AbstractNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Template variables of the daily suggestion e-mail ({@code recommendation-digest.html}). */
@Slf4j
@Service
@NotificationTypeHandler(NotificationType.RECOMMENDATION_DIGEST)
public class RecommendationDigestNotification extends AbstractNotification {

    private static final String USER_NAME = "userName";
    private static final String PRODUCTS = "products";
    private static final String UNSUBSCRIBE_URL = "unsubscribeUrl";

    @Override
    protected void prepareContext(final NotificationContext<?> context) {
        final RecommendationDigestPayload payload = (RecommendationDigestPayload) context.getPayload();
        // subject and templatePath are already set by RecommendationDigestServiceImp.
        context.setTemplateVariables(Map.of(
                USER_NAME, payload.getUserContactDto().getName(),
                PRODUCTS, payload.getProducts(),
                UNSUBSCRIBE_URL, payload.getUnsubscribeUrl()
        ));
    }
}
