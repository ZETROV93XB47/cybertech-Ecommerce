package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.UUID;

/**
 * Test fixture builder for {@link ProductEntity}. Presets {@code uuid} explicitly because builders
 * bypass {@code BaseEntity#prePersist}.
 */
public final class ProductEntityBuilder {

    private ProductEntityBuilder() {
    }

    public static ProductEntity aValidProduct() {
        return aValidProductBuilder().build();
    }

    public static ProductEntity.ProductEntityBuilder<?, ?> aValidProductBuilder() {
        return ProductEntity.builder()
                .uuid(UUID.randomUUID())
                .name("Sample Product")
                .price(new BigDecimal("99.99"))
                .brand(Brand.ASUS)
                .category(Category.COMPUTER)
                .photo("https://cdn.example.com/sample.jpg")
                .stock(10)
                .reservedStock(0)
                .description("A sample product for tests")
                .attributes(new HashMap<>());
    }
}
