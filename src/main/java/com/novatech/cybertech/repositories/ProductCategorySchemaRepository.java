package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.ProductCategorySchemaEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategorySchemaRepository extends CrudBaseRepository<ProductCategorySchemaEntity, Long> {

    Optional<ProductCategorySchemaEntity> findByCategoryKey(String categoryKey);

    List<ProductCategorySchemaEntity> findByActiveTrue();

    boolean existsByCategoryKey(String categoryKey);
}
