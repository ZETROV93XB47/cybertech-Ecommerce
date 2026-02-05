package com.novatech.cybertech.entities;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Getter
@ToString
@SuperBuilder
@MappedSuperclass
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEntity<ID extends Number & Serializable & Comparable<ID>> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private ID id;

    @EqualsAndHashCode.Include
    @JdbcTypeCode(SqlTypes.UUID)
    //@UuidGenerator(style = UuidGenerator.Style.TIME) // Force la génération v7 (séquentielle)
    @Column(name = "uuid", updatable = false, nullable = false, unique = true)
    private UUID uuid;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    public void prePersist() {
        if (uuid == null) {
            uuid = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
