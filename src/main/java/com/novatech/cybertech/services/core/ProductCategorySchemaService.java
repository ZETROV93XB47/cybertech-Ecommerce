package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.product.ProductCategorySchemaCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductCategorySchemaUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductCategorySchemaResponseDto;

import java.util.List;

/**
 * Admin-facing CRUD on the product-category-schema registry. Every mutation invalidates the
 * runtime {@code ProductCategorySchemaCache} locally and publishes a Redis Pub/Sub message so
 * sibling instances converge without a redeploy — see {@code ProductCategorySchemaCacheInvalidationListener}.
 */
public interface ProductCategorySchemaService {

    List<ProductCategorySchemaResponseDto> getAll();

    /** Also used by the public read endpoint. */
    ProductCategorySchemaResponseDto getByCategoryKey(String categoryKey);

    ProductCategorySchemaResponseDto create(ProductCategorySchemaCreateRequestDto request);

    ProductCategorySchemaResponseDto update(String categoryKey, ProductCategorySchemaUpdateRequestDto request);

    void deleteByCategoryKey(String categoryKey);
}
