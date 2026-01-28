package com.novatech.cybertech.entities;

import com.novatech.cybertech.exceptions.QuantityChangeResult;
import com.novatech.cybertech.exceptions.QuantityRejected;
import com.novatech.cybertech.exceptions.QuantityUpdated;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.novatech.cybertech.exceptions.QuantityRejectionReason.AMOUNT_TO_DECREASE_BIGGER_THAN_CURRENT_QUANTITY;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@Table(name = "cartItemTable")
@EqualsAndHashCode(callSuper = true)
public class CartItemEntity extends BaseEntity<Long> {

    @Setter
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unitPrice", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "cartId", nullable = false)
    @ToString.Exclude
    private CartEntity cart;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "productEntity", nullable = false)
    @ToString.Exclude
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


//    public QuantityChangeResult decreaseQuantity(final int amount) {
//        return switch (amount) {
//            case int amountToDecrease when amountToDecrease > this.quantity -> new QuantityRejected(AMOUNT_TO_DECREASE_BIGGER_THAN_CURRENT_QUANTITY);
//            default -> {
//                this.quantity -= amount;
//                yield new QuantityUpdated(this.quantity);
//            }
//        };
//    }


    public Integer increaseQuantity(final Integer amount) {
        quantity -= amount;
        return quantity;
    }

}