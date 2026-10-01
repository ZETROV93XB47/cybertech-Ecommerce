package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

//TODO: à refactorer plus tard
public interface ProductManagementService extends CrudBaseService<UUID, ProductCreateRequestDto, ProductUpdateRequestDto, ProductResponseDto, String> {
    /**
     * Creates a product and uploads 1 or more photos (unbounded, but realistically 1-5) to S3.
     * {@code ProductCreateRequestDto} carries no photo field — the resulting URLs are set
     * directly on the entity, in upload order.
     */
    @Transactional
    ProductResponseDto createWithImage(ProductCreateRequestDto productCreateRequestDto, List<MultipartFile> images, String keycloakId);

    // Added missing method to honor interface-first convention
    /**
     * Paginated read of every product, used by the admin catalog screen.
     *
     * @param pageable Spring Data pagination + sort hint.
     * @return one page of {@link ProductResponseDto}.
     */
    Page<ProductResponseDto> getAll(final Pageable pageable);

    // Added missing method to honor interface-first convention
    /**
     * Elasticsearch-backed product search exposed by the public {@code /search} endpoint.
     *
     * @param productSearchRequestDto the search criteria (keywords, filters, pagination).
     * @return one page of {@link ProductResponseDto} mapped from {@code ProductDocument}.
     */
    Page<ProductResponseDto> searchProducts(final ProductSearchRequestDto productSearchRequestDto);

    // Added missing method to honor interface-first convention
    /**
     * Paginated read of best-selling products, ranked by aggregate sales count.
     *
     * @param pageable Spring Data pagination + sort hint.
     * @return one page of {@link ProductResponseDto} ordered by sales rank.
     */
    Page<ProductResponseDto> getBestSellers(final Pageable pageable);
}