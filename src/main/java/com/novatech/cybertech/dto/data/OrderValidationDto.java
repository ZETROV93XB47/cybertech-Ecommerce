package com.novatech.cybertech.dto.data;

import com.novatech.cybertech.entities.BankCardEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class OrderValidationDto {
    private final boolean isUserActive;
    private final List<Long> productIds;
    final Map<UUID, Integer> productsByQuantityMap;
    private final BankCardEntity userDefaultBankCard;
}
