package com.novatech.cybertech.dto.response.stockalert;

import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.enums.StockAlertStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A back-in-stock alert of the connected user. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockAlertResponseDto {
    private UUID uuid;
    private ProductResponseDto product;
    private StockAlertStatus status;
    private LocalDateTime requestedAt;
}
