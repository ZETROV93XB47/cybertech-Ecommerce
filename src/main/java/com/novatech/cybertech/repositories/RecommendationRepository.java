package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.RecommendationEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Products sent in the suggestion e-mail ({@code RECOMMENDATION_DIGEST_JOB}): one row per product
 * sent, {@code generatedAt} = send date.
 */
@Repository
public interface RecommendationRepository extends CrudBaseRepository<RecommendationEntity, Long> {

    /** {@code true} when the user already received a suggestion e-mail after {@code since}. */
    boolean existsByUser_IdAndGeneratedAtAfter(Long userId, LocalDateTime since);

    /** Products sent to the user after {@code since}, so the next e-mails don't repeat them. */
    @Query("""
            SELECT DISTINCT r.product.uuid
            FROM   RecommendationEntity r
            WHERE  r.user.id = :userId
            AND    r.generatedAt > :since
            """)
    Set<UUID> findProductUuidsSentSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
