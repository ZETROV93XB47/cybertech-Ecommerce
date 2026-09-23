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
// Closes the check-then-insert race in WishlistServiceImp#addProductToMyWishlist: unlike the
// cart (where a Redis lock already serialises adds and this same kind of constraint is just a
// cheap declarative backstop), there is no lock here — two concurrent adds for the same
// (user, product) both pass the pre-check before either commits, so this constraint is the only
// thing actually preventing the duplicate. The service catches the resulting
// DataIntegrityViolationException and turns it into the same ProductAlreadyInWishlist the
// pre-check throws.
@Table(name = "wishlistTable", uniqueConstraints = @UniqueConstraint(name = "uk_wishlist_user_product", columnNames = {"userId", "productId"}))
@ToString(callSuper = true, exclude = {"user","product"})
@EqualsAndHashCode(callSuper = true, exclude = {"user","product"})
public class WishlistEntity extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "productId", nullable = false)
    private ProductEntity product;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;
}