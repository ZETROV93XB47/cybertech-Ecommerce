package com.novatech.cybertech.entities;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@ToString
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class BaseEntity<ID extends Number & Serializable & Comparable<ID>> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private ID id;

    @EqualsAndHashCode.Include
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "uuid", updatable = false, nullable = false, unique = true)
    private UUID uuid;

    @CreatedDate
    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    public void prePersist() {
        if (uuid == null) {
            uuid = UuidCreator.getTimeOrderedEpoch();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
