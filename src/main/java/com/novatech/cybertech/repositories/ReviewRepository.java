package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.ReviewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
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

    /**
     * One page of a product's published reviews (not flagged hateful), author fetched in the same
     * query since the public listing shows their first name.
     */
    @Query(value = "select r from ReviewEntity r join fetch r.userEntity "
            + "where r.productEntity.uuid = :productUuid and r.isHateful = false",
            countQuery = "select count(r) from ReviewEntity r "
                    + "where r.productEntity.uuid = :productUuid and r.isHateful = false")
    Page<ReviewEntity> findPublishedByProductUuid(@Param("productUuid") UUID productUuid, Pageable pageable);

    /**
     * One page of the reviews written by the user, product fetched in the same query for its name.
     * Includes reviews flagged hateful: the author still sees and can edit or delete them.
     */
    @Query(value = "select r from ReviewEntity r join fetch r.productEntity "
            + "where r.userEntity.keycloakId = :keycloakId",
            countQuery = "select count(r) from ReviewEntity r where r.userEntity.keycloakId = :keycloakId")
    Page<ReviewEntity> findByAuthorKeycloakId(@Param("keycloakId") String keycloakId, Pageable pageable);

    /** Number of a product's published reviews per rating; ratings nobody gave are absent. */
    @Query("select r.rating as rating, count(r) as count from ReviewEntity r "
            + "where r.productEntity.uuid = :productUuid and r.isHateful = false group by r.rating")
    List<RatingCount> countPublishedByRating(@Param("productUuid") UUID productUuid);

    /** Average rating and number of a product's published reviews; absent without any. */
    @Query("select r.productEntity.uuid as productUuid, avg(r.rating) as averageRating, count(r) as reviewCount "
            + "from ReviewEntity r where r.productEntity.uuid = :productUuid and r.isHateful = false "
            + "group by r.productEntity.uuid")
    Optional<RatingStats> findPublishedRatingStats(@Param("productUuid") UUID productUuid);

    /** {@link #findPublishedRatingStats} for every product with at least one published review (reindexing). */
    @Query("select r.productEntity.uuid as productUuid, avg(r.rating) as averageRating, count(r) as reviewCount "
            + "from ReviewEntity r where r.isHateful = false group by r.productEntity.uuid")
    List<RatingStats> findPublishedRatingStatsOfAllProducts();

    /** Projection of the published-rating statistics of one product. */
    interface RatingStats {
        UUID getProductUuid();

        Double getAverageRating();

        Long getReviewCount();
    }

    /** Projection of {@link #countPublishedByRating}. */
    interface RatingCount {
        Integer getRating();

        Long getCount();
    }
}
