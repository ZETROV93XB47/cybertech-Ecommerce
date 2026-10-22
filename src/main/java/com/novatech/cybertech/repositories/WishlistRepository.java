package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.WishlistEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistEntity, Long> {
    Optional<WishlistEntity> findByUuid(UUID uuid);
    void deleteByUuid(UUID uuid);

    Optional<WishlistEntity> findByUser_KeycloakIdAndProduct_Uuid(String keycloakId, UUID productUuid);
    Page<WishlistEntity> findAllByUser_KeycloakId(String keycloakId, Pageable pageable);
    boolean existsByUser_KeycloakIdAndProduct_Uuid(String keycloakId, UUID productUuid);
}