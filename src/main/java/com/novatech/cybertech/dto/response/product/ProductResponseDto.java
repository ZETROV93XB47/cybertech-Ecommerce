package com.novatech.cybertech.dto.response.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data // Génère getters, setters, toString, equals, hashCode
@Builder
@AllArgsConstructor
public class ProductResponseDto {
    private final String name;
    private final BigDecimal price;
    private final String brand;
    private final String category;
    //private final String photo;
    private final String description;
    private final Map<String, Object> attributes;
}
