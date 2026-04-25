package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.ReviewEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface ReviewRepository extends CrudBaseRepository<ReviewEntity, Long> {

    /**
     * Returns the set of product UUIDs the given user has already reviewed.
     * Used by {@code getReviewableProducts} to filter out products with an existing review.
     */
    @Query("select r.productEntity.uuid from ReviewEntity r where r.userEntity.keycloakId = :keycloakId")
    Set<UUID> findReviewedProductUuidsByUserKeycloakId(@Param("keycloakId") String keycloakId);
}
