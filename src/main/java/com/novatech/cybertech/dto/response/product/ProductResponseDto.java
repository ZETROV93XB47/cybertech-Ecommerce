package com.novatech.cybertech.dto.response.product;

import com.novatech.cybertech.entities.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data // Génère getters, setters, toString, equals, hashCode
@Builder
@AllArgsConstructor
public class ProductResponseDto {

    private String name;
    private BigDecimal price;
    private Brand brand;
    private Category category;
    private String cpu;
    private String gpu;
    private Ram ram;
    private SSD ssd;
    private DisplayType displayType;
    private DisplaySize displaySize;
    private Os os;
    private String connectivity;
    private String photo;
    private String description;
}
