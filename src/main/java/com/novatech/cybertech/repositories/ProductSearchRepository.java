package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    void deleteByUuid(final UUID uuid);
}
