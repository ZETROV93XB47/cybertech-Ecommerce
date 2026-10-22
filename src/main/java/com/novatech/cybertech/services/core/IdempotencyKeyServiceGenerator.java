package com.novatech.cybertech.services.core;

import java.util.List;

public interface IdempotencyKeyServiceGenerator {
    String generateKey(final String orderUUID, final List<String> context);

    default String generateKey(final String orderUUID, final String action) {
        return generateKey(orderUUID, List.of(action));
    }
}
