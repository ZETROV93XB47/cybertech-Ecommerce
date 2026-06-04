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
// UNIQUE(userId) — declarative "one cart per user" data invariant, created by Hibernate's
// `ddl-auto=update` from JPA metadata (e.g. for Testcontainers integration tests) and also
// declared explicitly in src/main/resources/sql/databaseSchemaInitFile.sql for the production
// init path. NOTE: this constraint used to back the BUG-160 first-insert-race retry (layer 3,
// the SELECT ... FOR UPDATE + DataIntegrityViolationException one-shot retry). That layer was
// removed — the per-user Redis lock now serialises the cart-add read-modify-write, including the
// first insert — so this constraint is no longer load-bearing for concurrency; it is kept purely
// as a cheap integrity guard. See CartServiceImp.addItemsToCart for the rationale.
@Table(name = "cartTable", uniqueConstraints = @UniqueConstraint(name = "uk_cart_user", columnNames = "userId"))
@ToString(callSuper = true, exclude = {"cartItems", "userEntity"})
@EqualsAndHashCode(callSuper = true, exclude = {"cartItems", "userEntity"})
public class CartEntity extends BaseEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false, unique = true) // C'est Cart qui porte la clé étrangère (voir SQL)
    private UserEntity userEntity;

    @OneToMany(mappedBy = "cart", orphanRemoval = true, cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CartItemEntity> cartItems;
}