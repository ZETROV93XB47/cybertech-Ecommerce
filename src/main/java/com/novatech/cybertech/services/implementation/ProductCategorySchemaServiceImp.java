package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.product.ProductCategorySchemaCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductCategorySchemaUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductCategorySchemaResponseDto;
import com.novatech.cybertech.entities.ProductCategorySchemaEntity;
import com.novatech.cybertech.exceptions.UnknownProductCategoryException;
import com.novatech.cybertech.repositories.ProductCategorySchemaRepository;
import com.novatech.cybertech.services.core.ProductCategorySchemaCache;
import com.novatech.cybertech.services.core.ProductCategorySchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static com.novatech.cybertech.constants.CyberTechAppConstants.PRODUCT_CATEGORY_SCHEMA_CHANGED_CHANNEL;

/**
 * See {@link ProductCategorySchemaService} for the contract's intent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategorySchemaServiceImp implements ProductCategorySchemaService {

    private final ProductCategorySchemaRepository productCategorySchemaRepository;
    private final ProductCategorySchemaCache productCategorySchemaCache;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategorySchemaResponseDto> getAll() {
        return productCategorySchemaRepository.findAll().stream()
                .sorted(Comparator.comparing(ProductCategorySchemaEntity::getCategoryKey))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductCategorySchemaResponseDto getByCategoryKey(final String categoryKey) {
        return toResponse(loadOrThrow(categoryKey));
    }

    @Override
    @Transactional
    public ProductCategorySchemaResponseDto create(final ProductCategorySchemaCreateRequestDto request) {
        if (productCategorySchemaRepository.existsByCategoryKey(request.categoryKey())) {
            throw new IllegalArgumentException("A schema for category '" + request.categoryKey() + "' is already registered");
        }

        // Fail fast on a malformed schema — never persist garbage the validation engine can't use.
        productCategorySchemaCache.compile(request.jsonSchema());

        final ProductCategorySchemaEntity saved = productCategorySchemaRepository.save(
                ProductCategorySchemaEntity.builder()
                        .categoryKey(request.categoryKey())
                        .label(request.label())
                        .jsonSchema(request.jsonSchema())
                        .active(true)
                        .build());

        notifyChanged(saved.getCategoryKey());
        log.info("Registered product category schema '{}'", saved.getCategoryKey());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProductCategorySchemaResponseDto update(final String categoryKey, final ProductCategorySchemaUpdateRequestDto request) {
        final ProductCategorySchemaEntity schema = loadOrThrow(categoryKey);

        if (request.label() != null) {
            schema.setLabel(request.label());
        }
        if (request.jsonSchema() != null) {
            productCategorySchemaCache.compile(request.jsonSchema());
            schema.setJsonSchema(request.jsonSchema());
        }
        if (request.active() != null) {
            schema.setActive(request.active());
        }

        final ProductCategorySchemaEntity saved = productCategorySchemaRepository.save(schema);
        notifyChanged(categoryKey);
        log.info("Updated product category schema '{}'", categoryKey);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteByCategoryKey(final String categoryKey) {
        final ProductCategorySchemaEntity schema = loadOrThrow(categoryKey);
        productCategorySchemaRepository.delete(schema);
        notifyChanged(categoryKey);
        log.info("Deleted product category schema '{}'", categoryKey);
    }

    private void notifyChanged(final String categoryKey) {
        productCategorySchemaCache.invalidate(categoryKey);
        stringRedisTemplate.convertAndSend(PRODUCT_CATEGORY_SCHEMA_CHANGED_CHANNEL, categoryKey);
    }

    private ProductCategorySchemaEntity loadOrThrow(final String categoryKey) {
        return productCategorySchemaRepository.findByCategoryKey(categoryKey)
                .orElseThrow(() -> new UnknownProductCategoryException("Unknown product category: " + categoryKey));
    }

    private ProductCategorySchemaResponseDto toResponse(final ProductCategorySchemaEntity entity) {
        return ProductCategorySchemaResponseDto.builder()
                .uuid(entity.getUuid())
                .categoryKey(entity.getCategoryKey())
                .label(entity.getLabel())
                .jsonSchema(entity.getJsonSchema())
                .active(entity.isActive())
                .build();
    }
}
