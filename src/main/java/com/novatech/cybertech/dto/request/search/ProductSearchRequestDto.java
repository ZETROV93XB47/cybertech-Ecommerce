package com.novatech.cybertech.dto.request.search;

import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequestDto {
    private String keyword;
    private Double priceMin;
    private Double priceMax;
    private List<Category> categories; // Ton Enum
    private List<Brand> brands;     // Ton Enum

    /**
     * C'est ici que la magie opère.
     * Le front-end envoie : {"ram": ["16GB", "32GB"], "ssd": ["512GB"]}
     */
    private Map<String, List<String>> attributes;
}