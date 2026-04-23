package com.novatech.cybertech.fixtures.dto;

import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.UUID;

/**
 * Tiny DTO factories for the product surface.
 *
 * <p>Note: {@link ProductResponseDto#getUuid()} is declared as {@link String} (BUG-034 in
 * progress.md). Fixtures here pass {@code uuid.toString()} so STRICT JSON tests don't
 * surface the typed mismatch.
 */
public final class ProductDtoFixtures {

    private ProductDtoFixtures() {
    }

    public static ProductCreateRequestDto aValidCreateRequest() {
        return aValidCreateRequestBuilder().build();
    }

    public static ProductCreateRequestDto.ProductCreateRequestDtoBuilder aValidCreateRequestBuilder() {
        return ProductCreateRequestDto.builder()
                .name("Sample Product")
                .price(new BigDecimal("99.99"))
                .brand(Brand.ASUS)
                .category(Category.COMPUTER)
                .photo("https://cdn.example.com/sample.jpg")
                .stock(10)
                .description("A sample product for tests")
                .attributes(new HashMap<>());
    }

    public static ProductUpdateRequestDto aValidUpdateRequest() {
        ProductUpdateRequestDto dto = new ProductUpdateRequestDto();
        dto.setProductUuid(UUID.randomUUID());
        dto.setName("Updated Product");
        dto.setPrice(new BigDecimal("149.99"));
        dto.setBrand(Brand.HP);
        dto.setCategory(Category.COMPUTER);
        dto.setPhoto("https://cdn.example.com/updated.jpg");
        dto.setStock(5);
        dto.setDescription("Updated description");
        return dto;
    }

    public static ProductResponseDto aSampleProductResponse() {
        return aSampleProductResponseBuilder().build();
    }

    public static ProductResponseDto.ProductResponseDtoBuilder aSampleProductResponseBuilder() {
        return ProductResponseDto.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Sample Product")
                .price(new BigDecimal("99.99"))
                .brand(Brand.ASUS.name())
                .category(Category.COMPUTER.name())
                .photoUrl("https://cdn.example.com/sample.jpg")
                .description("A sample product for tests")
                .attributes(new HashMap<>());
    }
}
