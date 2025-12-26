package com.novatech.cybertech.dto.request.order;

import lombok.Data;

import java.util.UUID;

@Data
public class OrderCancellationRequestDto {
    private UUID orderUuid;
}
