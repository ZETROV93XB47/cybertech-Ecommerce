package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.entities.document.ProductDocument;
import org.springframework.data.domain.Page;

public interface ProductSearchService {
    Page<ProductDocument> search(final ProductSearchRequestDto productSearchRequestDto);
}
