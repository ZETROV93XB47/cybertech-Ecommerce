package com.novatech.cybertech.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Slf4j
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cartItemTable")
@ToString(callSuper = true, exclude = {"cart", "productEntity"})
@EqualsAndHashCode(callSuper = true, exclude = {"cart", "productEntity"})
public class CartItemEntity extends BaseEntity<Long> {

    @Setter
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartId", nullable = false)
    private CartEntity cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "productEntity", nullable = false)
    private ProductEntity productEntity;

    @CreationTimestamp
    @Column(name = "addedAt", nullable = false, updatable = false)
    private LocalDateTime addedAt;


    public Integer decreaseQuantity(final Integer amount) {
        if (amount >= quantity) {
            this.quantity = 0;
            return 0;
        } else {
            this.quantity -= amount;
            return this.quantity;
        }
    }

    public Integer increaseQuantity(final Integer amount) {
        log.info("quantity before increase : {}", this.quantity);
        log.info("amount to increase : {}", amount);
        this.quantity = this.quantity + amount;
        log.info("quantity after increase : {}", this.quantity);
        return quantity;
    }

}