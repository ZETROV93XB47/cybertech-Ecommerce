package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends CrudBaseRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(final String email);

    Optional<UserEntity> findByKeycloakId(final String keycloakId);

    Optional<UserEntity> findByKeycloakIdAndIsActive(final String keycloakId, final boolean isActive);

    boolean existsByKeycloakIdAndIsActive(final String keycloakId, final boolean isActive);

    boolean existsByKeycloakId(String keycloakId);

    Optional<UserEntity> findByRecommendationEmailUnsubscribeToken(UUID token);

    /**
     * Ids of the suggestion e-mail recipients (active, not unsubscribed) after {@code afterId},
     * in id order: the job reads them page by page with the last id of the previous page.
     */
    @Query("""
            SELECT u.id
            FROM   UserEntity u
            WHERE  u.isActive = true
            AND    u.recommendationEmailEnabled = true
            AND    u.id > :afterId
            ORDER  BY u.id
            """)
    List<Long> findRecommendationEmailRecipientIdsAfter(@Param("afterId") Long afterId, Pageable pageable);
}
