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
import com.novatech.cybertech.services.core.AttributesFactory;
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

import java.util.Collection;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProductManagementServiceImp implements ProductManagementService {

    public static final String PRODUCTS_S3_BUCKET_NAME = "products";

    private final S3Service s3Service;
    private final ProductMapper productMapper;
    private final AttributesFactory attributesFactory;
    private final ProductRepository productRepository;
    private final ProductSearchService productSearchService;
    private final ProductSearchRepository productSearchRepository;
    private final ProductValidationService productValidationService;


    @Override
    @Transactional(readOnly = true)
    public Collection<ProductResponseDto> getAll() {
        return productMapper.mapFromEntityToResponseDto(productRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getAll(final Pageable pageable) {
        return productRepository.findAll(pageable).map(productMapper::mapFromEntityToResponseDto);
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

        ProductDocument productDocument = productMapper.mapFromProductEntityToProductDocument(savedProductEntity);
        productDocument.setAttributes(attributesFactory.create(productCreateRequestDto.getCategory(), productCreateRequestDto.getAttributes()));
        productSearchRepository.save(productDocument);

        return productMapper.mapFromEntityToResponseDto(savedProductEntity);
    }

    @Override
    @Transactional
    public ProductResponseDto createWithImage(final ProductCreateRequestDto productCreateRequestDto, final MultipartFile image) {
        if (image != null && !image.isEmpty()) {
            productCreateRequestDto.setPhoto(s3Service.uploadFile(image, PRODUCTS_S3_BUCKET_NAME));
        }

        return create(productCreateRequestDto);
    }

    @Override
    @Transactional
    public ProductResponseDto update(final ProductUpdateRequestDto productUpdateRequestDto) {
        final UUID uuid = productUpdateRequestDto.getProductUuid();
        final ProductEntity existing = productRepository.lockByUuid(uuid)
                .orElseThrow(() -> new ProductNotFoundException("No product with the UUID : " + uuid + " found"));

        if (productUpdateRequestDto.getName() != null) existing.setName(productUpdateRequestDto.getName());
        if (productUpdateRequestDto.getPrice() != null) existing.setPrice(productUpdateRequestDto.getPrice());
        if (productUpdateRequestDto.getBrand() != null) existing.setBrand(productUpdateRequestDto.getBrand());
        if (productUpdateRequestDto.getCategory() != null) existing.setCategory(productUpdateRequestDto.getCategory());
        if (productUpdateRequestDto.getPhoto() != null) existing.setPhoto(productUpdateRequestDto.getPhoto());
        if (productUpdateRequestDto.getStock() != null) existing.setStock(productUpdateRequestDto.getStock());
        if (productUpdateRequestDto.getDescription() != null) existing.setDescription(productUpdateRequestDto.getDescription());

        final ProductEntity saved = productRepository.save(existing);

        final ProductDocument document = productMapper.mapFromProductEntityToProductDocument(saved);
        // FIX(ES-SYNC): mirror the create() flow — attributes must be re-applied via the factory so Elasticsearch reflects updated category-specific typed fields (CPU, RAM, screen size, etc.)
        document.setAttributes(attributesFactory.create(saved.getCategory(), saved.getAttributes()));
        productSearchRepository.save(document);

        return productMapper.mapFromEntityToResponseDto(saved);
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
        if (uuids != null) {
            uuids.forEach(productSearchRepository::deleteByUuid);
        }
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
