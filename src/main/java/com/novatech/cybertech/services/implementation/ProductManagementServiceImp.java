package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.validator.ProductValidationService;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.mappers.entity.ProductMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.services.core.ProductManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProductManagementServiceImp implements ProductManagementService {

    private final ProductMapper productMapper;
    private final ProductRepository productRepository;
    private final ProductValidationService productValidationService;

    @Override
    @Transactional(readOnly = true)
    public Collection<ProductResponseDto> getAll() {
        return productMapper.mapFromEntityToResponseDto(productRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDto getByUUID(UUID uuid) {
        return productMapper.mapFromEntityToResponseDto(productRepository.findByUuid(uuid).orElseThrow(() -> new ProductNotFoundException("No product with the UUID : " + uuid + " found")));
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<ProductResponseDto> getByUUIDs(Collection<UUID> uuids) {
        return productMapper.mapFromEntityToResponseDto(productRepository.findAllByUuidIn(uuids));
    }

    @Override
    @Transactional
    public ProductResponseDto create(ProductCreateRequestDto productCreateRequestDto) {
        return productMapper.mapFromEntityToResponseDto(productRepository.save(productMapper.mapFromCreationRequestToEntity(productCreateRequestDto)));
    }

    @Override
    @Transactional
    public ProductResponseDto update(final ProductUpdateRequestDto productCreateRequestDto) {
        return productMapper.mapFromEntityToResponseDto(productRepository.save(productMapper.mapFromUpdateRequestToEntity(productCreateRequestDto)));
    }

    @Override
    @Transactional
    public void deleteByUUID(UUID uuid) {
        productRepository.deleteByUuid(uuid);
    }

    @Override
    @Transactional
    public void deleteByUUIDs(Collection<UUID> uuids) {
        productRepository.deleteAllByUuidIn(uuids);
    }

    @Transactional
    public ProductEntity temporarySaveProductEntity(final ProductCreateRequestDto productCreateRequestDto) {
        productValidationService.validateAttributes(productCreateRequestDto.getCategory(), productCreateRequestDto.getAttributes());
        return productRepository.save(productMapper.mapFromCreationRequestToEntity(productCreateRequestDto));
    }
}
