package com.novatech.cybertech.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cartTable")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"cartItems","userEntity"})
public class CartEntity extends BaseEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false) // C'est Cart qui porte la clé étrangère (voir SQL)
    private UserEntity userEntity;

    @CreationTimestamp
    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "cart", orphanRemoval = true, cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<CartItemEntity> cartItems;
}