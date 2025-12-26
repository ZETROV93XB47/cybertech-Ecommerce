package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.ProductDocument;
import com.novatech.cybertech.entities.enums.Category;

import java.util.List;
import java.util.Map;

public interface ProductSearchService {
    List<ProductDocument> searchByAttributes(final Category category, final Map<String, String> filters);
}
