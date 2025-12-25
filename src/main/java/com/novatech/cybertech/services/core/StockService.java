package com.novatech.cybertech.services.core;

import java.util.Map;
import java.util.UUID;

public interface StockService {
    void reserveStock(UUID orderUuid, Map<UUID, Integer> quantities);

    void commitStock(UUID orderUuid);

    void releaseStock(UUID orderUuid);
}
