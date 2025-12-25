package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.ProductDocument;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    List<ProductDocument> findByNameContainingIgnoreCase(final String name);

    @Query("{\"bool\": {\"should\": [" +
            "{\"match\": {\"name\": \"?0\"}}, " +
            "{\"match\": {\"description\": \"?0\"}}" +
            "]}}")
    List<ProductDocument> fullTextSearch(final String keyword);
}
