package com.novatech.cybertech.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One product of the suggestion e-mail. {@code imageUrl} is null when the product has no photo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedProductDto {

    private String name;
    private BigDecimal price;
    private String imageUrl;
    private String productUrl;
}
