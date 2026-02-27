package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.entities.validator.ProductValidationService;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.mappers.entity.ProductMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ProductSearchRepository;
import com.novatech.cybertech.services.core.ProductManagementService;
import com.novatech.cybertech.services.core.ProductSearchService;
import com.novatech.cybertech.services.core.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProductManagementServiceImp implements ProductManagementService {

    private final ProductMapper productMapper;
    private final ProductRepository productRepository;
    private final ProductSearchService productSearchService;
    private final ProductSearchRepository productSearchRepository;
    private final ProductValidationService productValidationService;
    private final S3Service s3Service;


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
        productValidationService.validateAttributes(productCreateRequestDto.getCategory(), productCreateRequestDto.getAttributes());
        final ProductEntity savedProductEntity = productRepository.save(productMapper.mapFromCreationRequestToEntity(productCreateRequestDto));

        productSearchRepository.save(productMapper.mapFromProductEntityToProductDocument(savedProductEntity));

        return productMapper.mapFromEntityToResponseDto(savedProductEntity);
    }

    @Override
    @Transactional
    public ProductResponseDto createWithImage(ProductCreateRequestDto productCreateRequestDto, MultipartFile image) {
        if (image != null && !image.isEmpty()) {
            String imageUrl = s3Service.uploadFile(image, "products");
            productCreateRequestDto.setPhoto(imageUrl);
        }


        // On délègue à la méthode create existante qui gère déjà la validation et la sauvegarde
        return create(productCreateRequestDto);
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
        productSearchRepository.deleteByUuid(uuid);
    }

    @Override
    @Transactional
    public void deleteByUUIDs(Collection<UUID> uuids) {
        productRepository.deleteAllByUuidIn(uuids);
    }

    public List<ProductResponseDto> searchProducts(final ProductSearchRequestDto productSearchRequestDto) {
        final List<ProductDocument> productDocuments = productSearchService.search(productSearchRequestDto);
        return productDocuments.stream().map(productMapper::mapFromProductDocumentToProductResponseDto).toList();
    }


    @Transactional
    public ProductEntity temporarySaveProductEntity(final ProductCreateRequestDto productCreateRequestDto) {
        productValidationService.validateAttributes(productCreateRequestDto.getCategory(), productCreateRequestDto.getAttributes());
        final ProductEntity savedProductEntity = productRepository.save(productMapper.mapFromCreationRequestToEntity(productCreateRequestDto));
        productSearchRepository.save(productMapper.mapFromProductEntityToProductDocument(savedProductEntity));

        //return productMapper.mapFromEntityToResponseDto(savedProductEntity);
        return savedProductEntity;
    }

    @Transactional
    public List<ProductResponseDto> getBestSellers(final Integer numberOfProducts) {
        final Pageable pageable = PageRequest.of(0, numberOfProducts);

        final List<ProductEntity> productEntities = productRepository.findBestSellers(pageable);

        return productEntities.stream()
                .map(productMapper::mapFromEntityToResponseDto)
                .toList();
    }
}
