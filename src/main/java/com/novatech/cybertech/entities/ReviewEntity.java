package com.novatech.cybertech.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.hibernate.validator.constraints.Range;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "reviewTable")
@ToString(callSuper = true, exclude = {"productEntity", "userEntity"})
@EqualsAndHashCode(callSuper = true, exclude = {"productEntity", "userEntity"})
public class ReviewEntity extends BaseEntity<Long> {

    @Range(min = 1, max = 5, message = "Rating must be between 1 and 5")
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "isHateful", nullable = false)
    private Boolean isHateful;

    /**
     * Order the reviewed product was bought in, checked at creation. Nullable: reviews posted
     * before the column existed are backfilled by the 2026-10-20 migration when a matching order
     * is found.
     */
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "orderUuid")
    private UUID orderUuid;

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "reviewId", nullable = false)
    private ProductEntity productEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private UserEntity userEntity;
}