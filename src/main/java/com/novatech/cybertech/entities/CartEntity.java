package com.novatech.cybertech.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cartTable")
@ToString(callSuper = true, exclude = {"cartItems", "userEntity"})
@EqualsAndHashCode(callSuper = true, exclude = {"cartItems", "userEntity"})
public class CartEntity extends BaseEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false) // C'est Cart qui porte la clé étrangère (voir SQL)
    private UserEntity userEntity;

    @OneToMany(mappedBy = "cart", orphanRemoval = true, cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CartItemEntity> cartItems;
}