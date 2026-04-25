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
// BUG-160 (PRE-2) — UNIQUE(userId) at the entity level so Hibernate's `ddl-auto=update`
// also creates the constraint when the schema is generated from JPA metadata (e.g. for
// Testcontainers integration tests). The matching constraint is also declared explicitly
// in src/main/resources/sql/databaseSchemaInitFile.sql for the production init path.
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