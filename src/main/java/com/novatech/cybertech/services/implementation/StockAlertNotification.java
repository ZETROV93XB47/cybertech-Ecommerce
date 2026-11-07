package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.NotificationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.StockAlertPayload;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.services.core.AbstractNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** Template variables of the back-in-stock e-mail ({@code stock-alert.html}). */
@Slf4j
@Service
@NotificationTypeHandler(NotificationType.STOCK_ALERT)
public class StockAlertNotification extends AbstractNotification {

    private static final String USER_NAME = "userName";
    private static final String PRODUCT_NAME = "productName";
    private static final String PRICE = "price";
    private static final String IMAGE_URL = "imageUrl";
    private static final String PRODUCT_URL = "productUrl";

    @Override
    protected void prepareContext(final NotificationContext<?> context) {
        final StockAlertPayload payload = (StockAlertPayload) context.getPayload();
        // HashMap, not Map.of: imageUrl may be null (product without photo).
        final Map<String, Object> variables = new HashMap<>();
        variables.put(USER_NAME, payload.getUserContactDto().getName());
        variables.put(PRODUCT_NAME, payload.getProductName());
        variables.put(PRICE, payload.getPrice());
        variables.put(IMAGE_URL, payload.getImageUrl());
        variables.put(PRODUCT_URL, payload.getProductUrl());
        context.setTemplateVariables(variables);
    }
}
