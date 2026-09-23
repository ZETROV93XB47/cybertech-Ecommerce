package com.novatech.cybertech.dto.request.search;

import com.novatech.cybertech.entities.enums.Brand;
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
    private String category;

    private List<Brand> brands;     // Ton Enum

    /**
     * C'est ici que la magie opère.
     * Le front-end envoie : {"ram": ["16GB", "32GB"], "ssd": ["512GB"]}
     */
    //private Map<String, List<String>> attributes;
    private Map<String, List<String>> attributes;

    // Pour les ranges numériques (ex: RAM min/max)
    private Map<String, RangeFilter> numericRanges;

    // Boxed (not primitive int): Jackson binds this DTO through its all-args constructor, where a
    // property missing from the JSON body is passed as a literal null — that can't be assigned to
    // a primitive int and blows up as "Malformed JSON request body" before validation even runs.
    // A missing page/size must be a legal, absent value here; ProductSearchServiceImp applies the
    // actual default (DEFAULT_PAGE_SIZE_PRODUCT_SEARCH) when building the Pageable. @Min still
    // catches an explicit size=0 as a clean 400 instead of a raw PageRequest.of() crash.
    @Min(value = 0, message = "Page must be >= 0")
    private Integer page;

    @Min(value = 1, message = "Size must be >= 1")
    private Integer size;
}