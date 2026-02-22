package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.WishlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistEntity, Long> {
    Optional<WishlistEntity> findByUuid(UUID uuid);
    void deleteByUuid(UUID uuid);
    
    Optional<WishlistEntity> findByUser_KeycloakIdAndProduct_Uuid(String keycloakId, UUID productUuid);
    List<WishlistEntity> findAllByUser_KeycloakId(String keycloakId);
    boolean existsByUser_KeycloakIdAndProduct_Uuid(String keycloakId, UUID productUuid);
}