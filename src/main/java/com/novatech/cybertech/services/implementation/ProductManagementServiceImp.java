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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProductManagementServiceImp implements ProductManagementService {

    public static final String PRODUCTS_S3_BUCKET_NAME = "products";

    private final S3Service s3Service;
    private final ProductMapper productMapper;
    private final ProductRepository productRepository;
    private final ProductSearchService productSearchService;
    private final ProductSearchRepository productSearchRepository;
    private final ProductValidationService productValidationService;


    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getAll(final Pageable pageable) {
        return productRepository.findAll(pageable).map(productMapper::mapFromEntityToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDto getByUUID(final UUID uuid, final String keycloakId) {
        return productMapper.mapFromEntityToResponseDto(productRepository.findByUuid(uuid).orElseThrow(() -> new ProductNotFoundException("No product with the UUID : " + uuid + " found")));
    }

    @Override
    @Transactional
    public ProductResponseDto create(final ProductCreateRequestDto productCreateRequestDto, final String keycloakId) {
        productValidationService.validateAttributes(productCreateRequestDto.getCategory(), productCreateRequestDto.getAttributes());

        return persistAndIndex(productMapper.mapFromCreationRequestToEntity(productCreateRequestDto));
    }

    /**
     * {@code ProductCreateRequestDto} carries no photo field at all — on this path the caller
     * sends raw image files, not URLs, so there is nothing meaningful a DTO field could hold.
     * The uploaded URLs are set directly on the freshly-mapped entity instead.
     */
    @Override
    @Transactional
    public ProductResponseDto createWithImage(final ProductCreateRequestDto productCreateRequestDto, final List<MultipartFile> images, final String keycloakId) {
        productValidationService.validateAttributes(productCreateRequestDto.getCategory(), productCreateRequestDto.getAttributes());

        final ProductEntity entity = productMapper.mapFromCreationRequestToEntity(productCreateRequestDto);

        if (images != null && !images.isEmpty()) {
            final List<String> uploadedUrls = images.stream()
                    .filter(image -> image != null && !image.isEmpty())
                    .map(image -> s3Service.uploadFile(image, PRODUCTS_S3_BUCKET_NAME))
                    .toList();
            if (!uploadedUrls.isEmpty()) {
                entity.setPhotos(uploadedUrls);
            }
        }

        return persistAndIndex(entity);
    }

    private ProductResponseDto persistAndIndex(final ProductEntity entity) {
        final ProductEntity savedProductEntity = productRepository.save(entity);

        final ProductDocument productDocument = productMapper.mapFromProductEntityToProductDocument(savedProductEntity);
        productSearchRepository.save(productDocument);

        return productMapper.mapFromEntityToResponseDto(savedProductEntity);
    }

    @Override
    @Transactional
    public ProductResponseDto update(final ProductUpdateRequestDto productUpdateRequestDto, final String keycloakId) {
        final UUID uuid = productUpdateRequestDto.getProductUuid();
        final ProductEntity existing = productRepository.lockByUuid(uuid)
                .orElseThrow(() -> new ProductNotFoundException("No product with the UUID : " + uuid + " found"));

        if (productUpdateRequestDto.getName() != null) existing.setName(productUpdateRequestDto.getName());
        if (productUpdateRequestDto.getPrice() != null) existing.setPrice(productUpdateRequestDto.getPrice());
        if (productUpdateRequestDto.getBrand() != null) existing.setBrand(productUpdateRequestDto.getBrand());
        if (productUpdateRequestDto.getCategory() != null) existing.setCategory(productUpdateRequestDto.getCategory());
        if (productUpdateRequestDto.getPhotos() != null) existing.setPhotos(productUpdateRequestDto.getPhotos());
        if (productUpdateRequestDto.getStock() != null) existing.setStock(productUpdateRequestDto.getStock());
        if (productUpdateRequestDto.getDescription() != null) existing.setDescription(productUpdateRequestDto.getDescription());
        if (productUpdateRequestDto.getAttributes() != null) {
            // Re-validate against the (possibly just-changed) category so attributes and
            // category can never diverge — attributes were previously write-once at creation.
            productValidationService.validateAttributes(existing.getCategory(), productUpdateRequestDto.getAttributes());
            existing.setAttributes(productUpdateRequestDto.getAttributes());
        }

        final ProductEntity saved = productRepository.save(existing);

        final ProductDocument document = productMapper.mapFromProductEntityToProductDocument(saved);
        productSearchRepository.save(document);

        return productMapper.mapFromEntityToResponseDto(saved);
    }

    @Override
    @Transactional
    public void deleteByUUID(final UUID uuid, final String keycloakId) {
        productRepository.deleteByUuid(uuid);
        productSearchRepository.deleteByUuid(uuid);
    }

    @Override
    public Page<ProductResponseDto> searchProducts(final ProductSearchRequestDto productSearchRequestDto) {
        final Page<ProductDocument> productDocuments = productSearchService.search(productSearchRequestDto);
        return productDocuments.map(productMapper::mapFromProductDocumentToProductResponseDto);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getBestSellers(final Pageable pageable) {
        return productRepository.findBestSellers(pageable).map(productMapper::mapFromEntityToResponseDto);
    }
}
