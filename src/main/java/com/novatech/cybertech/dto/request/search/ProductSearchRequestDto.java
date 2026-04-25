package com.novatech.cybertech.dto.request.search;

import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Min(value = 100, message = "Le prix minimum doit être supérieur ou égal à 100")
    private Double priceMin;

    @Max(value = 1000000, message = "Le prix maximum doit être supérieur ou égal à 1000000")
    private Double priceMax;

    // Category is now optional: a null value means "no category filter"
    // (keyword-only or cross-category search). The search service guards on null.
    private Category category; // Ton Enum

    private List<Brand> brands;     // Ton Enum

    /**
     * C'est ici que la magie opère.
     * Le front-end envoie : {"ram": ["16GB", "32GB"], "ssd": ["512GB"]}
     */
    //private Map<String, List<String>> attributes;
    private Map<String, List<String>> attributes;

    // Pour les ranges numériques (ex: RAM min/max)
    private Map<String, RangeFilter> numericRanges;

    private int page;
    private int size;
}