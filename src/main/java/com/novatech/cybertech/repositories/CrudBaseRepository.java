package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface CrudBaseRepository<T extends BaseEntity, U extends Number> extends JpaRepository<T, U> {
    Optional<T> findByUuid(final UUID uuid);

    List<T> findAllByUuidIn(final Collection<UUID> uuids);

    void deleteByUuid(final UUID uuid);

    void deleteAllByUuidIn(final Collection<UUID> uuids);
}
