package com.novatech.cybertech.services.core;

import java.util.List;

public interface IdempotencyKeyServiceGenerator {
    String generateKey(final String orderUUID, final List<String> orderProductsUUIDs);
}
