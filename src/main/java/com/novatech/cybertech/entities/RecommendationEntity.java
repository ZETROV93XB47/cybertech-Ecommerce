package com.novatech.cybertech.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "recommendationTable")
@ToString(callSuper = true, exclude = {"product", "user"})
@EqualsAndHashCode(callSuper = true, exclude = {"product", "user"})
public class RecommendationEntity extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "productId", nullable = false)
    private ProductEntity product;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "generatedAt", nullable = false)
    private LocalDateTime generatedAt;
}