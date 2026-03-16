package com.novatech.cybertech.dto.response.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class ProductResponseDto {
    private final String name;
    private final String uuid;
    private final BigDecimal price;
    private final String brand;
    private final String category;
    private final String photoUrl;
    private final String description;
    private final Map<String, Object> attributes;
}
