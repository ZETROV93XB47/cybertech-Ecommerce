package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

//TODO: à refactorer plus tard
public interface ProductManagementService extends CrudBaseService<UUID, ProductCreateRequestDto, ProductUpdateRequestDto, ProductResponseDto> {
    @Transactional
    ProductResponseDto createWithImage(ProductCreateRequestDto productCreateRequestDto, MultipartFile image);

    // FIX(INTERFACE-CONTRACT): added missing method to honor interface-first convention
    /**
     * Paginated read of every product, used by the admin catalog screen.
     *
     * @param pageable Spring Data pagination + sort hint.
     * @return one page of {@link ProductResponseDto}.
     */
    Page<ProductResponseDto> getAll(final Pageable pageable);

    // FIX(INTERFACE-CONTRACT): added missing method to honor interface-first convention
    /**
     * Elasticsearch-backed product search exposed by the public {@code /search} endpoint.
     *
     * @param productSearchRequestDto the search criteria (keywords, filters, pagination).
     * @return one page of {@link ProductResponseDto} mapped from {@code ProductDocument}.
     */
    Page<ProductResponseDto> searchProducts(final ProductSearchRequestDto productSearchRequestDto);

    // FIX(INTERFACE-CONTRACT): added missing method to honor interface-first convention
    /**
     * Paginated read of best-selling products, ranked by aggregate sales count.
     *
     * @param pageable Spring Data pagination + sort hint.
     * @return one page of {@link ProductResponseDto} ordered by sales rank.
     */
    Page<ProductResponseDto> getBestSellers(final Pageable pageable);
}