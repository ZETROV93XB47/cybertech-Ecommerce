package com.novatech.cybertech.dto.request.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class OrderCancellationRequestDto {

    @NotNull(message = "Order UUID cannot be null")
    private UUID orderUuid;
}
