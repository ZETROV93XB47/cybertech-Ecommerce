package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.entities.ProductDocument;

import java.util.List;

public interface ProductSearchService {
    List<ProductDocument> search(final ProductSearchRequestDto productSearchRequestDto);
}
